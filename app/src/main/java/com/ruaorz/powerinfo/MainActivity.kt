package com.ruaorz.powerinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruaorz.powerinfo.battery.BatteryInfoScreen
import com.ruaorz.powerinfo.root.RootChecker
import com.ruaorz.powerinfo.ui.style.LocalUiStyle
import com.ruaorz.powerinfo.ui.style.UiStyle
import com.ruaorz.powerinfo.ui.style.UiStylePreference
import com.ruaorz.powerinfo.ui.theme.PowerInfoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            // 单例读取/持有当前界面风格；style 变化时整棵树重组，实现 miuix/material 全局换肤。
            val uiStyle = remember { UiStylePreference(context) }

            CompositionLocalProvider(LocalUiStyle provides uiStyle) {
                AppTheme(style = uiStyle.style) {
                    RootGate()
                }
            }
        }
    }
}

/**
 * 按当前 [style] 选择主题包裹：
 * - [UiStyle.MATERIAL]：Material 3（[PowerInfoTheme]，支持 Android 12 动态色）。
 * - [UiStyle.MIUIX]：miuix 主题（按系统深浅色取亮/暗配色）。
 */
@Composable
private fun AppTheme(style: UiStyle, content: @Composable () -> Unit) {
    when (style) {
        UiStyle.MATERIAL -> PowerInfoTheme(content = content)
        UiStyle.MIUIX -> {
            val dark = isSystemInDarkTheme()
            MiuixTheme(
                colors = if (dark) darkColorScheme() else lightColorScheme(),
                content = content,
            )
        }
    }
}

/** Root 检测/授权的状态。 */
private enum class RootState {
    /** 检测中。 */
    CHECKING,

    /** 已获取 root 权限。 */
    GRANTED,

    /** 设备未 root（未找到 su）。 */
    UNAVAILABLE,

    /** 设备已 root，但用户拒绝了授权。 */
    DENIED,
}

/**
 * 启动时检测并申请 Root 权限：
 * 先静态预判是否有 su，再执行 `su -c` 触发超级用户授权弹窗。
 *
 * 无论是否获取到 Root，检测结束后都进入电源信息主界面：
 * 有 Root 时正常读取字段；无 Root 时各字段读取失败显示 N/A，
 * 并由主界面顶部提示条承载「重新授权」入口（详见 [BatteryInfoScreen]）。
 */
@Composable
fun RootGate(
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(RootState.CHECKING) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        state = RootState.CHECKING
        state = withContext(Dispatchers.IO) {
            when {
                !RootChecker.isRootAvailable() -> RootState.UNAVAILABLE
                RootChecker.requestRootAccess() -> RootState.GRANTED
                else -> RootState.DENIED
            }
        }
    }

    // 检测中：按当前风格显示加载态占位。
    if (state == RootState.CHECKING) {
        val style = LocalUiStyle.current.style
        when (style) {
            UiStyle.MATERIAL -> MaterialRootGateScaffold(modifier, state)
            UiStyle.MIUIX -> MiuixRootGateScaffold(modifier, state)
        }
        return
    }

    // 检测结束：无论有无 Root 都进入主界面。无 Root 时 hasRoot=false，
    // 主界面据此在顶部展示提示条，点击可触发重新检测/授权。
    BatteryInfoScreen(
        modifier = modifier,
        hasRoot = state == RootState.GRANTED,
        onRequestRoot = { attempt++ },
    )
}

@Composable
private fun MaterialRootGateScaffold(modifier: Modifier, state: RootState) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state == RootState.CHECKING) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.root_status_checking),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            // UNAVAILABLE / DENIED 主界面留空，仅通过对话框提示。
        }
    }
}

@Composable
private fun MiuixRootGateScaffold(modifier: Modifier, state: RootState) {
    MiuixScaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state == RootState.CHECKING) {
                MiuixCircularProgressIndicator()
                MiuixText(
                    text = stringResource(R.string.root_status_checking),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
