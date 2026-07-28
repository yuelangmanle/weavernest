package com.zhique.studio.ui

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhique.core.project.CapabilityRegistry
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ReleaseUpdatePolicy
import com.zhique.studio.BuildConfig
import com.zhique.studio.StudioLanguage
import com.zhique.studio.StudioUiState
import com.zhique.studio.StudioViewModel
import com.zhique.studio.TemplateCatalog
import com.zhique.studio.TemplateDefinition
import com.zhique.studio.WorkspaceTab
import com.zhique.studio.data.AiSettings
import com.zhique.studio.preview.WeaverPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhiqueApp(viewModel: StudioViewModel) {
    val state = viewModel.state
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.selectedDocument?.metadata?.displayName ?: "织雀",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (state.selectedDocument != null) {
                        TextButton(onClick = viewModel::closeProject) { Text("项目") }
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleLanguage) {
                        Text(if (state.language == StudioLanguage.Chinese) "中文" else "EN")
                    }
                    TextButton(onClick = { showSettings = true }) { Text(t(state, "设置", "Settings")) }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        state.selectedDocument?.let { document ->
            ProjectWorkspace(state, document, viewModel, modifier)
        } ?: HomeScreen(
            state = state,
            onCreate = { showCreate = true },
            onTemplates = { showTemplates = true },
            viewModel = viewModel,
            modifier = modifier
        )
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name -> viewModel.createBlankProject(name); showCreate = false }
        )
    }
    if (showTemplates) {
        TemplateCenterDialog(
            onDismiss = { showTemplates = false },
            onChoose = { template -> viewModel.createFromTemplate(template); showTemplates = false }
        )
    }
    if (showSettings) {
        SettingsDialog(state, viewModel, onDismiss = { showSettings = false })
    }
    state.aiDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAiDraft,
            title = { Text("AI 修改草案") },
            text = { Text(draft.take(2500)) },
            confirmButton = { TextButton(onClick = viewModel::applyAiDraftToIndex) { Text("写入并预览") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAiDraft) { Text("放弃") } }
        )
    }
}

@Composable
private fun HomeScreen(
    state: StudioUiState,
    onCreate: () -> Unit,
    onTemplates: () -> Unit,
    viewModel: StudioViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = context.displayName(uri)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (name.endsWith(".zip", true)) viewModel.importZip(name, bytes)
            else viewModel.importPlainText(name, bytes.toString(Charsets.UTF_8))
        }
    }
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("把一句需求变成可以使用的 Android 小工具", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("默认用 AI 创作，也可以直接粘贴或导入其他模型生成的完整项目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(t(state, "让 AI 帮我创建", "Create with AI")) }
        }
        item {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/html", "text/css", "text/javascript", "application/zip", "text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t(state, "导入 HTML / CSS / JS / ZIP", "Import code or ZIP")) }
        }
        item {
            OutlinedButton(onClick = onTemplates, modifier = Modifier.fillMaxWidth()) { Text(t(state, "模板中心", "Template center")) }
        }
        item { HorizontalDivider() }
        item { Text(t(state, "我的项目", "My projects"), style = MaterialTheme.typography.titleMedium) }
        items(state.documents, key = { it.metadata.id }) { document ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectProject(document.metadata.id) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(document.metadata.displayName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("${document.metadata.packageName} · ${document.metadata.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.notice?.let { notice -> item { NoticeBanner(notice) } }
    }
}

@Composable
private fun ProjectWorkspace(
    state: StudioUiState,
    document: ProjectDocument,
    viewModel: StudioViewModel,
    modifier: Modifier
) {
    Column(modifier) {
        ScrollableTabRow(selectedTabIndex = state.selectedTab.ordinal, edgePadding = 8.dp) {
            WorkspaceTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.selectedTab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(if (state.language == StudioLanguage.Chinese) tab.chinese else tab.english) }
                )
            }
        }
        when (state.selectedTab) {
            WorkspaceTab.Ai -> AiPanel(state, document, viewModel, Modifier.weight(1f))
            WorkspaceTab.Files -> FilesPanel(document, viewModel, Modifier.weight(1f))
            WorkspaceTab.Preview -> PreviewPanel(document, Modifier.weight(1f))
            WorkspaceTab.Capabilities -> CapabilitiesPanel(document, viewModel, Modifier.weight(1f))
            WorkspaceTab.Build -> BuildPanel(document, viewModel, Modifier.weight(1f))
            WorkspaceTab.Data -> DataPanel(document, viewModel, Modifier.weight(1f))
        }
        state.notice?.let { NoticeBanner(it, Modifier.padding(12.dp)) }
    }
}

@Composable
private fun AiPanel(state: StudioUiState, document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    var request by rememberSaveable(document.metadata.id) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("描述你希望改变的内容", style = MaterialTheme.typography.titleMedium)
            Text("织雀会先生成修改草案和预览，不会直接覆盖项目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(
                value = request,
                onValueChange = { request = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("例如：加入一个定位签到按钮") },
                minLines = 4
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.requestAi(request) }, enabled = request.isNotBlank() && !state.isAiRequestRunning) {
                    if (state.isAiRequestRunning) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    else Text("生成草案")
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.copyExternalPrompt())) }) { Text("复制外部提示词") }
            }
        }
        state.importAnalysis?.let { analysis ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("导入兼容性检查", fontWeight = FontWeight.SemiBold)
                        if (analysis.suggestions.isEmpty()) Text("未发现需要转换的常见设备 API。")
                        analysis.suggestions.forEach { suggestion -> Text("${suggestion.fileName}: 建议使用 ${suggestion.replacement}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    var selectedFile by rememberSaveable(document.metadata.id) { mutableStateOf("index.html") }
    val selectedContent = document.files[selectedFile].orEmpty()
    Column(modifier.padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            document.files.keys.sorted().take(4).forEach { file ->
                FilterChip(selected = file == selectedFile, onClick = { selectedFile = file }, label = { Text(file) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = selectedContent,
            onValueChange = { viewModel.updateFile(selectedFile, it) },
            modifier = Modifier.fillMaxSize(),
            label = { Text(selectedFile) },
            minLines = 18,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PreviewPanel(document: ProjectDocument, modifier: Modifier) {
    Column(modifier.padding(12.dp)) {
        Text("预览数据会在本项目内持续保存；导出 APK 时默认不会携带。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 1.dp) {
            WeaverPreview(document, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CapabilitiesPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    val selected = document.metadata.capabilities
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("能力与权限", style = MaterialTheme.typography.titleMedium)
            Text("导出的 APK 只声明此处选择的权限；系统仍会在实际使用时询问用户。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(CapabilityRegistry.all(), key = { it.id }) { capability ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(capability.title, fontWeight = FontWeight.SemiBold)
                        capability.availabilityNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Switch(
                        checked = capability.id in selected,
                        onCheckedChange = { enabled ->
                            viewModel.selectCapabilities(if (enabled) selected + capability.id else selected - capability.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    var displayName by rememberSaveable(document.metadata.id, document.metadata.displayName) { mutableStateOf(document.metadata.displayName) }
    var versionName by rememberSaveable(document.metadata.id, document.metadata.versionName) { mutableStateOf(document.metadata.versionName) }
    var packageName by rememberSaveable(document.metadata.id, document.metadata.packageName) { mutableStateOf(document.metadata.packageName) }
    val validation = CapabilityRegistry.validate(document.metadata.capabilities)
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("APK 构建", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("应用名称") }) }
        item { OutlinedTextField(versionName, { versionName = it }, Modifier.fillMaxWidth(), label = { Text("显示版本") }) }
        item {
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("包名") },
                enabled = !document.metadata.packageNameLocked,
                supportingText = { Text(if (document.metadata.packageNameLocked) "首次导出后包名已锁定，以保证覆盖更新。" else "首次导出后会锁定包名。") }
            )
        }
        item { Text("内部版本号：${document.metadata.versionCode}，每次成功导出自动递增。") }
        item { Text("Manifest 权限：${validation.manifestPermissions.joinToString().ifBlank { "无" }}", style = MaterialTheme.typography.bodySmall) }
        validation.restrictedCapabilities.takeIf { it.isNotEmpty() }?.let { restricted ->
            item { NoticeBanner("需要 Android 特殊系统流程：${restricted.joinToString()}") }
        }
        item {
            Button(
                onClick = {
                    viewModel.updateBuildMetadata(displayName, versionName, packageName)
                    viewModel.prepareBuild()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("生成本地构建计划") }
        }
        items(document.buildHistory.reversed()) { record ->
            Text("${record.versionName} (${record.versionCode}) · ${record.status} · ${record.message}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DataPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("项目数据", style = MaterialTheme.typography.titleMedium)
        Text("预览数据属于 ${document.metadata.displayName}，重启织雀后仍会保留。生成 APK 后，应用会使用独立的数据沙箱。")
        OutlinedButton(onClick = viewModel::clearPreviewData) { Text("清除预览数据") }
        NoticeBanner("正式 APK 会内置数据管理模块。签名密钥备份与恢复将在首次实际导出前要求设置备份密码。")
    }
}

@Composable
private fun SettingsDialog(state: StudioUiState, viewModel: StudioViewModel, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var showAiSettings by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("织雀 ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold) }
                item { Text("GitHub：https://github.com/${BuildConfig.GITHUB_REPOSITORY}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { uriHandler.openUri("https://github.com/${BuildConfig.GITHUB_REPOSITORY}") }) }
                item { Text("开发人员：月亮满了") }
                item { Text("QQ：3335196397") }
                item { HorizontalDivider() }
                item {
                    Button(onClick = viewModel::checkForUpdate, enabled = !state.isUpdateCheckRunning, modifier = Modifier.fillMaxWidth()) {
                        if (state.isUpdateCheckRunning) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                        else Text("检查 GitHub 更新")
                    }
                }
                state.releaseInfo?.let { release ->
                    val isNewer = ReleaseUpdatePolicy.isNewer(BuildConfig.VERSION_NAME, release.tagName)
                    item { Text("最新发布：${release.tagName}", fontWeight = FontWeight.SemiBold) }
                    item { Text(release.releaseNotes) }
                    if (isNewer && release.apkUrl != null) {
                        item { Button(onClick = viewModel::downloadLatestApk, modifier = Modifier.fillMaxWidth()) { Text("下载 ${release.apkName}") } }
                    }
                }
                item { HorizontalDivider() }
                item { Text("更新日志", fontWeight = FontWeight.SemiBold) }
                item { Text("0.2.0-alpha：建立 Kotlin/Compose 创作器、项目工作区、预览桥、能力目录、AI 提示词与 GitHub 更新设置。") }
                item { TextButton(onClick = { showAiSettings = true }) { Text("AI 接口设置") } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
    if (showAiSettings) {
        AiSettingsDialog(
            initial = viewModel.loadAiSettings(),
            onDismiss = { showAiSettings = false },
            onSave = { settings -> viewModel.saveAiSettings(settings); showAiSettings = false }
        )
    }
}

@Composable
private fun AiSettingsDialog(initial: AiSettings, onDismiss: () -> Unit, onSave: (AiSettings) -> Unit) {
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 接口设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI-compatible 地址") })
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型") })
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") })
                Text("DeepSeek 可使用 https://api.deepseek.com/v1；密钥只保存在本机安全存储。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(AiSettings(endpoint, model, apiKey)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建项目") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("工具名称") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onCreate(name.ifBlank { "未命名工具" }) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TemplateCenterDialog(onDismiss: () -> Unit, onChoose: (TemplateDefinition) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模板中心") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TemplateCatalog.all, key = { it.id }) { template ->
                    Card(Modifier.fillMaxWidth().clickable { onChoose(template) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(template.title, fontWeight = FontWeight.SemiBold)
                            Text("${template.category} · ${template.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun NoticeBanner(message: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

private fun t(state: StudioUiState, chinese: String, english: String): String = if (state.language == StudioLanguage.Chinese) chinese else english

private fun Context.displayName(uri: Uri): String {
    val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index)
    }
    return uri.lastPathSegment ?: "imported-project"
}
