package com.zhique.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhique.runtime.PreviewDataManager
import com.zhique.runtime.RuntimeHostCallbacks
import com.zhique.runtime.RuntimeProjectDataBackupManager
import com.zhique.runtime.SharedPreferencesRuntimeDataStore
import com.zhique.runtime.WebRuntimeHost
import com.zhique.runtime.bridge.RuntimeBridgeDispatcher
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.bridge.RuntimeEventDispatchers
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.capability.RuntimeCapabilityHandlers
import com.zhique.runtime.permission.AndroidRuntimeCapabilityControls
import kotlinx.coroutines.launch

private enum class DataBackupAction { Export, Import }

class GeneratedRuntimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) { GeneratedRuntimeApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratedRuntimeApp() {
    val context = LocalContext.current
    val definition = remember(context.applicationContext) { GeneratedProjectLoader.load(context) }
    val uiHost = rememberGeneratedRuntimeUiHost()
    val eventBus = remember { RuntimeEventBus(RuntimeEventDispatchers::main) }
    var log by remember { mutableStateOf("") }
    var runToken by remember { mutableIntStateOf(0) }
    var showData by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var backupAction by remember { mutableStateOf<DataBackupAction?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    val backupManager = remember(context.applicationContext) { RuntimeProjectDataBackupManager(context) }
    val scope = rememberCoroutineScope()
    val latestLog by rememberUpdatedState { value: String, error: Boolean -> log = if (error) "错误：$value" else value }
    val runtimeHost = remember(context.applicationContext, uiHost, eventBus) {
        WebRuntimeHost(
            context = context,
            dispatcher = RuntimeBridgeDispatcher(
                dataStore = SharedPreferencesRuntimeDataStore(context),
                handlers = RuntimeCapabilityHandlers.create(context, uiHost, eventBus),
                capabilityControls = AndroidRuntimeCapabilityControls(context, uiHost)
            ),
            eventBus = eventBus,
            callbacks = object : RuntimeHostCallbacks {
                override fun onPageReady() = latestLog("应用正在运行。", false)
                override fun onLog(message: String, isError: Boolean) = latestLog(message, isError)
                override fun onBlockedNavigation(url: String) = latestLog("已拦截不可信导航。", true)
            }
        )
    }
    DisposableEffect(runtimeHost) { onDispose(runtimeHost::close) }
    LaunchedEffect(definition, runToken) {
        runtimeHost.load(
            definition.runtimeProject,
            RuntimeSession(
                id = "generated-${System.currentTimeMillis()}-$runToken",
                projectId = definition.runtimeProject.id,
                selectedCapabilities = definition.capabilities,
                runtimeName = "generated-apk",
                androidApi = android.os.Build.VERSION.SDK_INT
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(definition.displayName) },
                actions = {
                    IconButton(onClick = { showConfig = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "运行时私密配置说明")
                    }
                    IconButton(onClick = { showData = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "应用数据管理")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { runtimeHost.container }, modifier = Modifier.fillMaxWidth().weight(1f))
            if (log.isNotBlank()) Text(log, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showData) {
        AlertDialog(
            onDismissRequest = { showData = false },
            title = { Text("应用数据") },
            text = { Text("可以加密备份或恢复本应用通过 weaver.data 和 weaver.storage 保存的数据与文件。私密 API 配置、浏览器缓存和织雀预览数据不会被导出。") },
            confirmButton = {
                TextButton(onClick = {
                    runtimeHost.stop()
                    PreviewDataManager(context).clearProjectData(definition.runtimeProject.id)
                    runToken += 1
                    showData = false
                }) { Text("清除本应用数据") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { backupAction = DataBackupAction.Export }) { Text("导出备份") }
                    TextButton(onClick = { backupAction = DataBackupAction.Import }) { Text("恢复备份") }
                    TextButton(onClick = { showData = false }) { Text("取消") }
                }
            }
        )
    }
    backupAction?.let { action ->
        AlertDialog(
            onDismissRequest = { backupAction = null; backupPassword = "" },
            title = { Text(if (action == DataBackupAction.Export) "导出加密数据备份" else "恢复加密数据备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份密码至少 12 位。恢复会替换当前应用的公开项目数据和项目文件，私密 API 配置不会被修改。")
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("备份密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = runCatching {
                            require(backupPassword.length >= 12) { "备份密码至少需要 12 位。" }
                            if (action == DataBackupAction.Export) {
                                val destination = uiHost.createDocument(definition.displayName + "-data.zqd")
                                    ?: throw IllegalStateException("用户取消了备份文件保存。")
                                backupManager.exportToUri(definition.runtimeProject.id, backupPassword, destination)
                                "加密数据备份已导出。"
                            } else {
                                val source = uiHost.openDocument(arrayOf("application/octet-stream"))
                                    ?: throw IllegalStateException("用户取消了备份文件选择。")
                                runtimeHost.stop()
                                backupManager.restoreFromUri(definition.runtimeProject.id, backupPassword, source)
                                runToken += 1
                                "数据备份已恢复。"
                            }
                        }
                        log = result.getOrElse { error ->
                            if (action == DataBackupAction.Import) runToken += 1
                            "数据备份未完成：" + (error.message ?: "未知错误")
                        }
                        backupAction = null
                        backupPassword = ""
                    }
                }) { Text(if (action == DataBackupAction.Export) "选择位置并导出" else "选择备份并恢复") }
            },
            dismissButton = { TextButton(onClick = { backupAction = null; backupPassword = "" }) { Text("取消") } }
        )
    }
    if (showConfig) {
        AlertDialog(
            onDismissRequest = { showConfig = false },
            title = { Text("私密运行时配置") },
            text = { Text("项目代码通过 weaver.config 在用户主动操作后保存 API Key 等私密值。值仅保存在本机加密存储中，不随 APK 打包、不写入运行日志。") },
            confirmButton = { TextButton(onClick = { showConfig = false }) { Text("知道了") } }
        )
    }
}
