package com.zhique.studio.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.zhique.core.stabilization.ApilotAvailability
import kotlinx.coroutines.delay

@Composable
fun ApilotGuidanceDialog(
    availability: ApilotAvailability,
    onDismiss: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenApplication: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    var remainingSeconds by remember(availability) { mutableIntStateOf(3) }
    if (availability is ApilotAvailability.NotInstalled) {
        LaunchedEffect(availability) {
            while (remainingSeconds > 0) {
                delay(1_000)
                remainingSeconds -= 1
            }
        }
    }
    val title = when (availability) {
        is ApilotAvailability.NotInstalled -> "需要安装 Apilot"
        is ApilotAvailability.InstalledIncompatible -> "Apilot 版本不兼容"
        is ApilotAvailability.InstalledDisabled -> "Apilot 已被停用"
        is ApilotAvailability.InstalledCompatible -> "Apilot 已就绪"
    }
    val message = when (availability) {
        is ApilotAvailability.NotInstalled -> "导入或导出 API 方案需要 Apilot。你可以继续留在织雀，或在确认后前往 Apilot 安装说明。"
        is ApilotAvailability.InstalledIncompatible -> "检测到 Apilot ${availability.versionName.orEmpty()}，但它没有提供完整的 API Profile V2 接口。请打开 Apilot 检查更新。"
        is ApilotAvailability.InstalledDisabled -> "系统已停用 Apilot。请先在应用信息页面启用它，再重新执行此操作。"
        is ApilotAvailability.InstalledCompatible -> "Apilot 已可使用。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message)
                if (availability is ApilotAvailability.NotInstalled) {
                    Spacer(Modifier.height(4.dp))
                    Text("织雀不会自动离开当前页面。")
                }
            }
        },
        confirmButton = {
            when (availability) {
                is ApilotAvailability.NotInstalled -> Button(
                    enabled = remainingSeconds == 0,
                    onClick = onOpenRepository
                ) {
                    Text(if (remainingSeconds == 0) "前往安装说明" else "${remainingSeconds} 秒后可前往安装说明")
                }
                is ApilotAvailability.InstalledIncompatible -> Button(onClick = onOpenApplication) { Text("打开 Apilot") }
                is ApilotAvailability.InstalledDisabled -> Button(onClick = onOpenAppSettings) { Text("打开应用设置") }
                is ApilotAvailability.InstalledCompatible -> Button(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            when (availability) {
                is ApilotAvailability.InstalledIncompatible -> OutlinedButton(onClick = onOpenRepository) { Text("查看更新说明") }
                else -> TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
