package com.ruaorz.powerinfo.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ruaorz.powerinfo.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * 电源信息主界面的 miuix 外壳：观感对齐 HyperOS。顶栏（自动刷新周期弹层、手动刷新、关于）与读数卡片列表。
 * 数据与副作用由 [BatteryInfoScreen] 承载并通过参数传入，本组件只负责渲染与交互回调。
 */
@Composable
internal fun MiuixBatteryInfoScreen(
    readings: List<BatteryReading>,
    loading: Boolean,
    interval: Long?,
    onIntervalChange: (Long?) -> Unit,
    onRefresh: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedIndex = REFRESH_OPTIONS.indexOfFirst { it.intervalMillis == interval }
        .coerceAtLeast(0)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.battery_screen_title),
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = stringResource(R.string.battery_action_auto_refresh),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        WindowListPopup(
                            show = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            ListPopupColumn {
                                REFRESH_OPTIONS.forEachIndexed { index, option ->
                                    DropdownImpl(
                                        text = option.label,
                                        optionSize = REFRESH_OPTIONS.size,
                                        isSelected = index == selectedIndex,
                                        index = index,
                                        onSelectedIndexChange = {
                                            onIntervalChange(option.intervalMillis)
                                            menuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.battery_action_refresh),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.about_title),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showLoadingPlaceholder(loading, readings)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.battery_loading),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = ContentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(readings) { reading ->
                    MiuixBatteryRow(reading)
                }
            }
        }
    }
}

@Composable
private fun MiuixBatteryRow(reading: BatteryReading) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { copyReadingToClipboard(context, reading) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = reading.name,
                style = MiuixTheme.textStyles.subtitle,
            )
            Text(
                text = reading.displayValue,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            reading.path?.let { path ->
                Text(
                    text = path,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
