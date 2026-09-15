package com.ruaorz.powerinfo.battery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ruaorz.powerinfo.about.AboutScreen
import com.ruaorz.powerinfo.ui.style.LocalUiStyle
import com.ruaorz.powerinfo.ui.style.UiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 自动刷新周期选项。
 *
 * @param label 菜单显示文本。
 * @param intervalMillis 刷新间隔（毫秒）；null 表示暂停自动刷新。
 */
internal data class RefreshOption(val label: String, val intervalMillis: Long?)

internal val REFRESH_OPTIONS = listOf(
    RefreshOption("暂停", null),
    RefreshOption("1s", 1000L),
    RefreshOption("5s", 5000L),
)

/**
 * 电源信息主界面入口：集中承载数据读取/自动刷新/关于页叠加等与风格无关的逻辑，
 * 再按当前界面风格（[LocalUiStyle]）把 UI 外壳分派给 Material 或 miuix 实现。
 *
 * 数据层（[BatteryRepository]、读取与自动刷新的 [LaunchedEffect]）在此复用，两套外壳仅负责渲染。
 */
@Composable
fun BatteryInfoScreen(
    modifier: Modifier = Modifier,
    hasRoot: Boolean = true,
    onRequestRoot: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { BatteryRepository(context) }
    val fields = remember { repository.loadFields() }

    var readings by remember { mutableStateOf<List<BatteryReading>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var interval by remember { mutableStateOf<Long?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    // 手动刷新与初始加载：refreshKey 变化时读取一次。
    LaunchedEffect(refreshKey) {
        loading = true
        readings = withContext(Dispatchers.IO) {
            repository.ensureRoot()
            repository.readAll(fields)
        }
        loading = false
    }

    // 自动刷新：选定周期后循环读取；切回暂停(null)时本协程被取消。
    // 用 try/finally 确保协程被取消（切换周期/暂停）时 loading 一定复位，
    // 否则手动刷新按钮会因 loading 卡在 true 而一直灰掉。
    LaunchedEffect(interval) {
        val period = interval ?: return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) { repository.ensureRoot() }
            while (true) {
                loading = true
                readings = withContext(Dispatchers.IO) { repository.readAll(fields) }
                loading = false
                delay(period)
            }
        } finally {
            loading = false
        }
    }

    // 界面销毁时释放常驻 root 会话。
    DisposableEffect(Unit) {
        onDispose { repository.release() }
    }

    // 主页常驻在底层，关于页叠在上面弹出/收回；关于页仅在显示时加入组合。
    // 关于页的进入/跟手/退出过渡由 AboutScreen 内部单一 Animatable 驱动，并按风格分派。
    Box(modifier = modifier.fillMaxSize()) {
        when (LocalUiStyle.current.style) {
            UiStyle.MATERIAL -> MaterialBatteryInfoScreen(
                readings = readings,
                loading = loading,
                interval = interval,
                hasRoot = hasRoot,
                onIntervalChange = { interval = it },
                onRefresh = { refreshKey++ },
                onOpenAbout = { showAbout = true },
                onRequestRoot = onRequestRoot,
            )

            UiStyle.MIUIX -> MiuixBatteryInfoScreen(
                readings = readings,
                loading = loading,
                interval = interval,
                hasRoot = hasRoot,
                onIntervalChange = { interval = it },
                onRefresh = { refreshKey++ },
                onOpenAbout = { showAbout = true },
                onRequestRoot = onRequestRoot,
            )
        }

        // 关于页覆盖在主页之上，退出动画播完后移除。
        if (showAbout) {
            AboutScreen(hasRoot = hasRoot, onExitFinished = { showAbout = false })
        }
    }
}

/** 加载态与初始空列表的占位判断，供两套外壳共用。 */
internal fun showLoadingPlaceholder(loading: Boolean, readings: List<BatteryReading>): Boolean =
    loading && readings.isEmpty()

/** 内容留白，供两套外壳共用。 */
internal val ContentPadding = PaddingValues(16.dp)

/**
 * 复制一条读数（字段名、字段值、目录路径）到剪贴板，并吐司提示。
 * 供 Material 与 miuix 两套外壳的卡片点击共用。
 */
internal fun copyReadingToClipboard(context: Context, reading: BatteryReading) {
    val text = buildString {
        append(reading.name)
        append('\n')
        append(reading.displayValue)
        reading.path?.let {
            append('\n')
            append(it)
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(reading.name, text))
    Toast.makeText(
        context,
        context.getString(
            com.ruaorz.powerinfo.R.string.battery_copied,
            reading.name.trimEnd(':', '：'),
        ),
        Toast.LENGTH_SHORT,
    ).show()
}
