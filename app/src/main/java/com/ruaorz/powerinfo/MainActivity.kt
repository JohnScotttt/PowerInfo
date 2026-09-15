package com.ruaorz.powerinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruaorz.powerinfo.battery.BatteryInfoScreen
import com.ruaorz.powerinfo.root.RootChecker
import com.ruaorz.powerinfo.ui.theme.PowerInfoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PowerInfoTheme {
                RootGate(onExit = { finish() })
            }
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
 * 未获取权限时弹出 Material 3 对话框提示。
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

    // 已授权：进入电源信息主界面（其自带 Scaffold）。
    if (state == RootState.GRANTED) {
        BatteryInfoScreen(modifier = modifier)
        return
    }

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

    if (state == RootState.UNAVAILABLE || state == RootState.DENIED) {
        RootRequiredDialog(
            denied = state == RootState.DENIED,
            onRetry = { attempt++ },
            onExit = onExit,
        )
    }
}

@Composable
private fun RootRequiredDialog(
    denied: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* 强制用户做出选择，不允许点击外部关闭 */ },
        title = { Text(stringResource(R.string.root_dialog_title)) },
        text = {
            Text(
                stringResource(
                    if (denied) R.string.root_dialog_message_denied
                    else R.string.root_dialog_message_unavailable
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.root_dialog_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.root_dialog_exit))
            }
        },
    )
}
