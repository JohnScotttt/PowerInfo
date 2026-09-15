package com.ruaorz.powerinfo.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ruaorz.powerinfo.about.AboutScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 自动刷新周期选项。
 *
 * @param label 菜单显示文本。
 * @param intervalMillis 刷新间隔（毫秒）；null 表示暂停自动刷新。
 */
private data class RefreshOption(val label: String, val intervalMillis: Long?)

private val REFRESH_OPTIONS = listOf(
    RefreshOption("暂停", null),
    RefreshOption("1s", 1000L),
    RefreshOption("5s", 5000L),
)

/**
 * 电源信息主界面：从定义文件读取各 sysfs 节点的值并以卡片列表展示。
 * 支持手动刷新，以及通过时钟菜单选择自动刷新周期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryInfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { BatteryRepository(context) }
    val fields = remember { repository.loadFields() }

    var readings by remember { mutableStateOf<List<BatteryReading>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var interval by remember { mutableStateOf<Long?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
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
    // 主页不随关于页开关重建（不会被 dispose 后重组），消除“卡一下”的首帧卡顿。
    // 关于页的进入/跟手/退出过渡全部由 AboutScreen 内部单一 Animatable 驱动（对齐 HMA 的中心缩放 + 尾段淡出）。
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.battery_screen_title)) },
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = stringResource(R.string.battery_action_auto_refresh),
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                REFRESH_OPTIONS.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                interval = option.intervalMillis
                                                menuExpanded = false
                                            },
                                            trailingIcon = {
                                                if (option.intervalMillis == interval) {
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
                            IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.battery_action_refresh),
                                )
                            }
                            IconButton(onClick = { showAbout = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.about_title),
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                if (loading && readings.isEmpty()) {
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(readings) { reading ->

                            BatteryRow(reading)
                        }
                    }
                }
            }

        // 关于页覆盖在主页之上。进入/跟手/退出全部由 AboutScreen 内部单一 Animatable 驱动，
        // 这里只负责「是否在组合中」：打开时立即加入，退出动画播完后由 onExitFinished 回调移除。
        if (showAbout) {
            AboutScreen(onExitFinished = { showAbout = false })
        }
    }
}

@Composable
private fun BatteryRow(reading: BatteryReading) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = reading.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = reading.displayValue,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            reading.path?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
