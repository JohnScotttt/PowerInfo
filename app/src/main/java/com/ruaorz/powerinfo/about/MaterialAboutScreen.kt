package com.ruaorz.powerinfo.about

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruaorz.powerinfo.R
import com.ruaorz.powerinfo.ui.style.LocalUiStyle
import com.ruaorz.powerinfo.ui.style.UiStyle

/**
 * 关于页的 Material 3 实现：应用信息头部卡片、界面风格选择框、关于文案分区、开发者列表。
 * 由 [AboutScreen] 在 [UiStyle.MATERIAL] 下调用；过渡动画由入口层通过 [modifier] 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialAboutScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = rememberVersionName()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            MaterialHeaderCard(
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
                MaterialUiStyleSelector()

                MaterialAboutSection(
                    title = stringResource(R.string.about_section_general_title),
                    body = stringResource(R.string.about_section_general_body),
                )

                MaterialDevelopersSection()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 界面风格选择框（Material）：卡片 + 下拉菜单，写入 [LocalUiStyle] 触发全局换肤。 */
@Composable
private fun MaterialUiStyleSelector() {
    val uiStyle = LocalUiStyle.current
    var expanded by remember { mutableStateOf(false) }

    val label = when (uiStyle.style) {
        UiStyle.MATERIAL -> stringResource(R.string.settings_ui_style_material)
        UiStyle.MIUIX -> stringResource(R.string.settings_ui_style_miuix)
    }

    Column {
        Text(
            text = stringResource(R.string.settings_ui_style_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_ui_style_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 用 Box 包住触发图标与菜单，让 DropdownMenu 锚定在右侧图标下方展开，
                // 否则菜单会以 Row 末尾的零宽占位为锚点而被翻转到左边。
                Box {
                    Icon(
                        imageVector = Icons.Filled.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        UiStyle.entries.forEach { style ->
                            val itemLabel = when (style) {
                                UiStyle.MATERIAL -> stringResource(R.string.settings_ui_style_material)
                                UiStyle.MIUIX -> stringResource(R.string.settings_ui_style_miuix)
                            }
                            DropdownMenuItem(
                                text = { Text(itemLabel) },
                                onClick = {
                                    uiStyle.select(style)
                                    expanded = false
                                },
                                trailingIcon = {
                                    if (style == uiStyle.style) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialHeaderCard(
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
            shape = RoundedCornerShape(24.dp),
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
                    MaterialLinkButton(
                        label = stringResource(R.string.about_link_github),
                        url = AboutContent.GITHUB_URL,
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
private fun MaterialLinkButton(
    label: String,
    url: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        modifier = modifier,
    ) {
        Text(label)
    }
}

@Composable
private fun MaterialAboutSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
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
private fun MaterialDevelopersSection() {
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
                modifier = Modifier.padding(start = 16.dp),
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
                        MaterialDeveloperRow(dev)
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialDeveloperRow(developer: Developer) {
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
