package com.ruaorz.powerinfo.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruaorz.powerinfo.R
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
 * 独立的关于页：应用信息头部卡片、关于文案分区、开发者列表，参考常见 OSS 应用的关于页设计。
 *
 * 进入、跟手预测性返回、退出全部由同一个 [progress]（0=完全显示，1=完全退出）驱动 graphicsLayer，
 * 只有一套动画源，避免多套动画叠加导致的闪烁。退出动画播完后回调 [onExitFinished] 将本页移出组合。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onExitFinished: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

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

    Scaffold(
        modifier = Modifier.graphicsLayer {
            // 中心缩放 1.0→0.9（对齐 HMA close_exit 的 scale）。
            val p = progress.value
            val scale = 1f - p * 0.1f
            scaleX = scale
            scaleY = scale
            // 淡出只在尾段发生（对齐 HMA close_exit：前 60% 保持不透明，后 40% 线性淡出），
            // 避免整段都半透明导致与主页叠加“发灰/闪烁”。
            alpha = 1f - ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
        },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { scope.launch { commitExit() } }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 头部卡片放在滚动区之外，始终固定在顶部（对应 HMA 里 include 与 ScrollView 平级的写法）。
            HeaderCard(
                versionName = versionName,
                context = context,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 固定头部与滚动区之间的一圈留白（对应 HMA 里 ScrollView 的 layout_marginTop）。
            // 放在滚动区外面，滚动时这段空隙始终保留、不会跟内容滚走。
            Spacer(modifier = Modifier.height(20.dp))

            // 只有下面这块内容可滚动。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AboutSection(
                    title = stringResource(R.string.about_section_general_title),
                    body = stringResource(R.string.about_section_general_body),
                )

                DevelopersSection()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HeaderCard(
    versionName: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    // 关于页图标使用打包进项目的 512×512 高清原图（drawable-nodpi/ic_about_logo.png），
    // 而非缩放启动器 mipmap，保证高密度屏下清晰。
    // 图标尺寸；卡片顶部留出图标一半高度，让图标“骑”在卡片上边缘（悬浮嵌入效果）。
    val iconSize = 96.dp

    Box(modifier = modifier.fillMaxWidth()) {
        // 技巧2：单张连续圆角卡片承载全部头部信息，用主题色统一染色。
        // （View 体系里是“上圆角 + 下圆角”两个 shape 同色拼接，Compose 一张卡即可等价。）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = iconSize / 2),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 技巧1：占位区顶住图标下半身（对应 View 里那段 48dp 的 Space）。
                Spacer(modifier = Modifier.height(iconSize / 2 + 12.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.about_support_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinkButton(
                        label = stringResource(R.string.about_link_github),
                        context = context,
                        modifier = Modifier.weight(1f),
                    )
                    LinkButton(
                        label = stringResource(R.string.about_link_telegram),
                        context = context,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 技巧1：悬浮图标居中对齐到 Box 顶部，中心正好落在卡片上边缘，
        // 上半身悬空、下半身压在卡片上。放在 Card 之后绘制，保证盖在卡片上层。
        // 原图四周留白约 24%（内容仅占中间 260/512），用 Crop + 放大 1.4 裁掉部分留白，
        // 使图案在圆角卡内的占比接近启动器自适应图标观感。
        Image(
            painter = painterResource(R.drawable.ic_about_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(iconSize)
                .clip(RoundedCornerShape(24.dp))
                .scale(1.4f),
        )
    }
}

@Composable
private fun LinkButton(
    label: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val comingSoon = stringResource(R.string.about_coming_soon)
    Button(
        onClick = {
            Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
    ) {
        Text(label)
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun DevelopersSection() {
    var expanded by remember { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.about_section_developers_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    AboutContent.DEVELOPERS.forEach { dev ->
                        DeveloperRow(dev)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperRow(developer: Developer) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (developer.profileUrl != null) {
                    Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(developer.profileUrl))
                            )
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (developer.avatarUrl != null) {
            // 优先使用构建时下载到 assets/avatars 的本地头像（无需实时访问网络）；
            // 若本地文件不存在（如下载失败），回退到网络 URL。
            val avatarModel = remember(developer.name, developer.avatarUrl) {
                val assetPath = "avatars/${developer.name}.png"
                val hasLocal = runCatching {
                    context.assets.open(assetPath).close(); true
                }.getOrDefault(false)
                if (hasLocal) "file:///android_asset/$assetPath" else developer.avatarUrl
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarModel)
                    .crossfade(true)
                    .build(),
                contentDescription = developer.name,
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Filled.Person),
                placeholder = rememberVectorPainter(Icons.Filled.Person),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = developer.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = developer.role,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
