package com.zhique.studio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhique.studio.integrations.ApilotProfile

@Composable
fun ApilotImportReviewDialog(
    profile: ApilotProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认导入 Apilot 方案") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("织雀将保存以下连接信息：", fontWeight = FontWeight.SemiBold)
                Text("地址：${profile.endpoint}")
                Text("模型：${profile.model}")
                Text("提供商：${profile.providerId}")
                Text("协议：${profile.protocolId}")
                Text(if (profile.apiKey.isNullOrBlank()) "不包含 API Key。" else "包含 API Key，密钥正文不会显示或写入日志。")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
