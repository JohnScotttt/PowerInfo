package com.ruaorz.powerinfo.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruaorz.powerinfo.R
import com.ruaorz.powerinfo.ui.style.LocalUiStyle
import com.ruaorz.powerinfo.ui.style.UiStyle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页的 miuix 实现：观感对齐 HyperOS。头部信息卡、界面风格选择框（[OverlayDropdownPreference]）、
 * 关于文案、开发者列表。由 [AboutScreen] 在 [UiStyle.MIUIX] 下调用；过渡动画由入口层通过 [modifier] 注入。
 *
 * miuix 的 [OverlayDropdownPreference] 需要一个 root [Scaffold] 承载弹层（renderInRootScaffold=true），
 * 本页自带 miuix [Scaffold] 满足该前提。
 */
@Composable
internal fun MiuixAboutScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = rememberVersionName()

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                            tint = MiuixTheme.colorScheme.onSurface,
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
            MiuixHeaderCard(
                versionName = versionName,
                context = context,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 界面风格选择框：置于「关于」区块之上。
                MiuixUiStyleSelector()

                MiuixAboutSection(
                    title = stringResource(R.string.about_section_general_title),
                    body = stringResource(R.string.about_section_general_body),
                )

                MiuixDevelopersSection()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 界面风格选择框（miuix）：用 [OverlayDropdownPreference]，选择后写入 [LocalUiStyle] 触发全局换肤。 */
@Composable
private fun MiuixUiStyleSelector() {
    val uiStyle = LocalUiStyle.current
    val items = listOf(
        stringResource(R.string.settings_ui_style_material),
        stringResource(R.string.settings_ui_style_miuix),
    )
    // items 顺序需与 UiStyle.entries 一致（MATERIAL=0, MIUIX=1）。
    val selectedIndex = uiStyle.style.ordinal

    Column {
        SmallTitle(
            text = stringResource(R.string.settings_ui_style_title),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                items = items,
                selectedIndex = selectedIndex,
                title = stringResource(R.string.settings_ui_style_title),
                onSelectedIndexChange = { index ->
                    UiStyle.entries.getOrNull(index)?.let { uiStyle.select(it) }
                },
            )
        }
    }
}

@Composable
private fun MiuixHeaderCard(
    versionName: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val iconSize = 96.dp

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = iconSize / 2),
            cornerRadius = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(iconSize / 2 + 12.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.title2,
                )
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.about_support_title),
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixLinkButton(
                        label = stringResource(R.string.about_link_github),
                        context = context,
                        modifier = Modifier.weight(1f),
                    )
                    MiuixLinkButton(
                        label = stringResource(R.string.about_link_telegram),
                        context = context,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

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
private fun MiuixLinkButton(
    label: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val comingSoon = stringResource(R.string.about_coming_soon)
    Button(
        onClick = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
        modifier = modifier,
    ) {
        Text(text = label, color = MiuixTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun MiuixAboutSection(title: String, body: String) {
    Column {
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = body,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MiuixDevelopersSection() {
    var expanded by remember { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(
                text = stringResource(R.string.about_section_developers_title),
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    AboutContent.DEVELOPERS.forEach { dev ->
                        MiuixDeveloperRow(dev)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixDeveloperRow(developer: Developer) {
    val context = LocalContext.current
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = developer.name,
        summary = developer.role,
        startAction = {
            if (developer.avatarUrl != null) {
                val avatarModel = remember(developer.name, developer.avatarUrl) {
                    val assetPath = "avatars/${developer.name}.png"
                    val hasLocal = runCatching {
                        context.assets.open(assetPath).close(); true
                    }.getOrDefault(false)
                    if (hasLocal) "file:///android_asset/$assetPath" else developer.avatarUrl
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = developer.name,
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Filled.Person),
                    placeholder = rememberVectorPainter(Icons.Filled.Person),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(40.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onClick = developer.profileUrl?.let { url ->
            {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure { /* 无可处理该链接的应用时静默忽略 */ }
                    .let {}
            }
        },
    )
}
