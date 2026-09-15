package com.ruaorz.powerinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

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
                    RootGate(onExit = { finish() })
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
 * 先静态预判是否有 su，再执行 `su -c` 触发超级用户授权弹窗，
 * 未获取权限时按当前界面风格弹出对应风格的对话框提示。
 */
@Composable
fun RootGate(
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
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

    // 已授权：进入电源信息主界面（其自带 Scaffold，并按风格分派）。
    if (state == RootState.GRANTED) {
        BatteryInfoScreen(modifier = modifier)
        return
    }

    val style = LocalUiStyle.current.style
    when (style) {
        UiStyle.MATERIAL -> MaterialRootGateScaffold(modifier, state)
        UiStyle.MIUIX -> MiuixRootGateScaffold(modifier, state)
    }

    if (state == RootState.UNAVAILABLE || state == RootState.DENIED) {
        RootRequiredDialog(
            style = style,
            denied = state == RootState.DENIED,
            onRetry = { attempt++ },
            onExit = onExit,
        )
    }
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

@Composable
private fun RootRequiredDialog(
    style: UiStyle,
    denied: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val title = stringResource(R.string.root_dialog_title)
    val message = stringResource(
        if (denied) R.string.root_dialog_message_denied
        else R.string.root_dialog_message_unavailable
    )
    val retryLabel = stringResource(R.string.root_dialog_retry)
    val exitLabel = stringResource(R.string.root_dialog_exit)

    when (style) {
        UiStyle.MATERIAL -> AlertDialog(
            onDismissRequest = { /* 强制用户做出选择，不允许点击外部关闭 */ },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onRetry) { Text(retryLabel) }
            },
            dismissButton = {
                TextButton(onClick = onExit) { Text(exitLabel) }
            },
        )

        UiStyle.MIUIX -> WindowDialog(
            show = true,
            title = title,
            summary = message,
            // 强制用户做出选择：不响应点击外部/返回关闭（不提供 onDismissRequest）。
            onDismissRequest = null,
        ) {
            Column {
                MiuixTextButton(
                    text = retryLabel,
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixTextButton(
                    text = exitLabel,
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
    }
}
