package com.ruaorz.powerinfo.about

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ruaorz.powerinfo.ui.style.LocalUiStyle
import com.ruaorz.powerinfo.ui.style.UiStyle
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 页面过渡缓动：近似 HMA-OSS 所用的 @android:interpolator/fast_out_extra_slow_in
 * （Material 的 Emphasized 曲线，先快后极慢），让缩放过渡更有质感。
 */
private val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/** 进入/退出动画时长（ms），约等于 HMA 的 config_mediumAnimTime。 */
private const val TRANSITION_DURATION = 300

/**
 * 独立的关于页入口：负责统一的进入/跟手预测性返回/退出动画（对齐 HMA 的中心缩放 + 尾段淡出），
 * 再按当前界面风格（[LocalUiStyle]）分派到 Material 或 miuix 两套实现。
 *
 * 进入、跟手预测性返回、退出全部由同一个 [progress]（0=完全显示，1=完全退出）驱动 graphicsLayer，
 * 只有一套动画源，避免多套动画叠加导致的闪烁。退出动画播完后回调 [onExitFinished] 将本页移出组合。
 * 动画留在入口层包裹两套实现，避免每套各写一遍。
 */
@Composable
fun AboutScreen(hasRoot: Boolean, onExitFinished: () -> Unit) {
    // 单一进度：0=完全显示，1=完全退出（缩到 0.9 + 淡出，对齐 HMA 的 close_exit）。
    // 初值为 1 表示“缩小/透明”的入场起点，挂载后动到 0 完成进场。
    val progress = remember { Animatable(1f) }

    // 进场：从 1（缩小+透明）平滑动到 0（完全显示），对齐 HMA 的 open_enter 中心放大。
    LaunchedEffect(Unit) {
        progress.animateTo(0f, tween(TRANSITION_DURATION, easing = EmphasizedEasing))
    }

    // 退出：把 progress 推到底再把本页移出组合；供返回箭头与手势完成共用，只播这一段。
    suspend fun commitExit() {
        progress.animateTo(1f, tween(TRANSITION_DURATION, easing = EmphasizedEasing))
        onExitFinished()
    }

    // 预测性返回：手势进度(0~1)直接驱动同一个 progress，跟手预览主页；
    // 手势完成则续播到底再退出，取消则回弹归位。全程只有这一套动画，不会闪。
    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            commitExit()
        } catch (e: CancellationException) {
            progress.animateTo(0f, tween(TRANSITION_DURATION, easing = EmphasizedEasing))
            throw e
        }
    }

    val scope = rememberCoroutineScope()
    val onBack: () -> Unit = { scope.launch { commitExit() } }

    val animatedModifier = Modifier.graphicsLayer {
        // 中心缩放 1.0→0.9（对齐 HMA close_exit 的 scale）。
        val p = progress.value
        val scale = 1f - p * 0.1f
        scaleX = scale
        scaleY = scale
        // 淡出只在尾段发生（对齐 HMA close_exit：前 60% 保持不透明，后 40% 线性淡出），
        // 避免整段都半透明导致与主页叠加“发灰/闪烁”。
        alpha = 1f - ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
    }

    when (LocalUiStyle.current.style) {
        UiStyle.MATERIAL -> MaterialAboutScreen(animatedModifier, hasRoot, onBack)
        UiStyle.MIUIX -> MiuixAboutScreen(animatedModifier, hasRoot, onBack)
    }
}

/** 关于页展示用的应用版本号（读取失败时回退为 "-"）。 */
@Composable
internal fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }
}
