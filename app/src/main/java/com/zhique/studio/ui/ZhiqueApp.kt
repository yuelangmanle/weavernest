package com.zhique.studio.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zhique.core.project.CapabilityRegistry
import com.zhique.core.project.ExternalCodeImport
import com.zhique.core.project.ProjectDocument
import com.zhique.studio.BuildConfig
import com.zhique.studio.PreviewRuntimeStatus
import com.zhique.studio.StudioLanguage
import com.zhique.studio.StudioUiState
import com.zhique.studio.StudioViewModel
import com.zhique.studio.TemplateCatalog
import com.zhique.studio.TemplateDefinition
import com.zhique.studio.UpdateUiState
import com.zhique.studio.WorkspaceTab
import com.zhique.studio.data.AiSettings
import com.zhique.studio.integrations.ApilotV2
import com.zhique.studio.preview.WeaverPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhiqueApp(viewModel: StudioViewModel) {
    val state = viewModel.state
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var pasteIntoCurrentProject by rememberSaveable { mutableStateOf(false) }
    var showPasteScreen by rememberSaveable { mutableStateOf(false) }

    val apilotPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when {
            result.resultCode == Activity.RESULT_CANCELED -> viewModel.showNotice("已取消从 Apilot 选择 API 方案。")
            result.resultCode == Activity.RESULT_OK && result.data != null -> runCatching {
                ApilotV2.parsePickResult(context, result.data!!)
            }.onSuccess(viewModel::applyApilotProfile)
                .onFailure { viewModel.showNotice(it.message ?: "无法读取 Apilot 返回的方案。") }
            else -> viewModel.showNotice("Apilot 没有返回可用的 API 方案。")
        }
    }
    val launchApilotPicker: (Boolean) -> Unit = { includeApiKey ->
        if (ApilotV2.isAvailable(context)) {
            apilotPicker.launch(ApilotV2.createPickIntent(includeApiKey))
        } else {
            viewModel.showNotice("未安装 Apilot，正在打开其项目仓库供你安装。")
            uriHandler.openUri(ApilotV2.repositoryUrl)
        }
    }
    val launchApilotExport: (AiSettings, Boolean) -> Unit = { settings, includeApiKey ->
        if (ApilotV2.isAvailable(context)) {
            runCatching { context.startActivity(ApilotV2.createExportIntent(context, settings, includeApiKey)) }
                .onFailure { viewModel.showNotice(it.message ?: "无法打开 Apilot。") }
        } else {
            viewModel.showNotice("未安装 Apilot，正在打开其项目仓库供你安装。")
            uriHandler.openUri(ApilotV2.repositoryUrl)
        }
    }

    if (showPasteScreen) {
        PasteCodeScreen(
            replaceCurrentProject = pasteIntoCurrentProject,
            onBack = { showPasteScreen = false },
            onCommit = { name, source ->
                if (pasteIntoCurrentProject) viewModel.pasteIntoSelectedProject(source)
                else viewModel.createFromPastedCode(name, source)
                showPasteScreen = false
            },
            onZipImport = { name, bytes ->
                viewModel.importZip(name, bytes)
                showPasteScreen = false
            }
        )
    } else {
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
                ProjectWorkspace(
                    state = state,
                    document = document,
                    viewModel = viewModel,
                    onPasteIntoProject = {
                        pasteIntoCurrentProject = true
                        showPasteScreen = true
                    },
                    modifier = modifier
                )
            } ?: HomeScreen(
                state = state,
                onCreate = { showCreate = true },
                onPaste = {
                    pasteIntoCurrentProject = false
                    showPasteScreen = true
                },
                onTemplates = { showTemplates = true },
                viewModel = viewModel,
                modifier = modifier
            )
        }
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
        SettingsDialog(
            state = state,
            viewModel = viewModel,
            onPickApilot = launchApilotPicker,
            onExportApilot = launchApilotExport,
            onDismiss = { showSettings = false }
        )
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
    onPaste: () -> Unit,
    onTemplates: () -> Unit,
    viewModel: StudioViewModel,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("创作可以直接使用的 Android 小工具", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("从 AI、网页代码或 ZIP 开始。织雀会先让你确认导入内容，再进入编辑和运行。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = onPaste, modifier = Modifier.fillMaxWidth()) { Text("粘贴外部代码") }
        }
        item {
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(t(state, "让 AI 帮我创建", "Create with AI")) }
        }
        item {
            OutlinedButton(onClick = onTemplates, modifier = Modifier.fillMaxWidth()) { Text(t(state, "模板中心", "Template center")) }
        }
        item { HorizontalDivider() }
        item { Text(t(state, "我的项目", "My projects"), style = MaterialTheme.typography.titleMedium) }
        if (state.documents.isEmpty()) {
            item { NoticeBanner("还没有项目。粘贴一段 HTML、CSS 或 JavaScript，然后点击创建并打开编辑器。") }
        }
        items(state.documents, key = { it.metadata.id }) { document ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.selectProject(document.metadata.id) }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteCodeScreen(
    replaceCurrentProject: Boolean,
    onBack: () -> Unit,
    onCommit: (String, String) -> Unit,
    onZipImport: (String, ByteArray) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var projectName by rememberSaveable { mutableStateOf("导入项目") }
    var source by rememberSaveable { mutableStateOf("") }
    val draft = remember(projectName, source) {
        source.takeIf { it.isNotBlank() }?.let { ExternalCodeImport.prepare(projectName, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = context.displayName(uri)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (name.endsWith(".zip", true)) onZipImport(name, bytes)
            else {
                projectName = name.substringBeforeLast('.').ifBlank { "导入项目" }
                source = bytes.toString(Charsets.UTF_8)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (replaceCurrentProject) "粘贴到当前项目" else "粘贴外部代码") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!replaceCurrentProject) {
                OutlinedTextField(projectName, { projectName = it }, Modifier.fillMaxWidth(), label = { Text("项目名称") }, singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { source = clipboard.getText()?.text.orEmpty() }, modifier = Modifier.weight(1f)) { Text("粘贴剪贴板") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/html", "text/css", "text/javascript", "application/zip", "text/plain")) },
                    modifier = Modifier.weight(1f)
                ) { Text("导入文件 / ZIP") }
            }
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("粘贴 HTML、CSS 或 JavaScript") },
                placeholder = { Text("可以直接粘贴其他 AI 生成的完整网页代码") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            draft?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("导入检查", fontWeight = FontWeight.SemiBold)
                        Text("识别到：${it.detectedParts.joinToString { part -> part.label }}")
                        Text("将创建：${it.files.keys.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        if (it.analysis.suggestions.isNotEmpty()) {
                            Text("建议启用：${it.analysis.suggestedCapabilities.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(
                onClick = { onCommit(projectName, source) },
                enabled = source.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (replaceCurrentProject) "写入当前项目并打开编辑器" else "创建项目并打开编辑器") }
        }
    }
}

@Composable
private fun ProjectWorkspace(
    state: StudioUiState,
    document: ProjectDocument,
    viewModel: StudioViewModel,
    onPasteIntoProject: () -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = viewModel::runPreview, modifier = Modifier.weight(1f)) {
                Text(if (state.previewRuntime.status == PreviewRuntimeStatus.Running) "重新运行" else "运行")
            }
            OutlinedButton(onClick = onPasteIntoProject, modifier = Modifier.weight(1f)) { Text("粘贴代码") }
            if (state.previewRuntime.status == PreviewRuntimeStatus.Running) {
                TextButton(onClick = viewModel::stopPreview) { Text("停止") }
            }
        }
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
            WorkspaceTab.Preview -> PreviewPanel(state, document, viewModel, Modifier.weight(1f))
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
                    if (state.isAiRequestRunning) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
    var selectedFile by rememberSaveable(document.metadata.id) { mutableStateOf(document.files.keys.firstOrNull() ?: "index.html") }
    val selectedContent = document.files[selectedFile].orEmpty()
    Column(modifier.padding(12.dp)) {
        Text("代码编辑器", style = MaterialTheme.typography.titleMedium)
        Text("修改会自动保存在当前项目。完成后点击顶部“运行”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(document.files.keys.sorted()) { file ->
                FilterChip(selected = file == selectedFile, onClick = { selectedFile = file }, label = { Text(file) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = selectedContent,
            onValueChange = { viewModel.updateFile(selectedFile, it) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text(selectedFile) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun PreviewPanel(state: StudioUiState, document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    val runtime = state.previewRuntime
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("本地运行预览", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (runtime.status) {
                        PreviewRuntimeStatus.Idle -> "尚未运行"
                        PreviewRuntimeStatus.Running -> "正在运行"
                        PreviewRuntimeStatus.Stopped -> "已停止"
                        PreviewRuntimeStatus.Error -> "运行出现错误"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (runtime.status == PreviewRuntimeStatus.Running) {
                OutlinedButton(onClick = viewModel::stopPreview) { Text("停止") }
            } else {
                Button(onClick = viewModel::runPreview) { Text("运行") }
            }
        }
        Text("预览数据会在本项目内持续保存；导出 APK 时默认不会携带。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (runtime.status == PreviewRuntimeStatus.Running || runtime.status == PreviewRuntimeStatus.Error) {
            Surface(modifier = Modifier.fillMaxWidth().weight(1f), tonalElevation = 1.dp) {
                WeaverPreview(
                    document = document,
                    runToken = runtime.runToken,
                    isRunning = runtime.status == PreviewRuntimeStatus.Running,
                    onReady = viewModel::onPreviewReady,
                    onLog = viewModel::onPreviewLog,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            NoticeBanner("点击“运行”后会在此处打开项目。")
        }
        if (runtime.logs.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.height(112.dp).padding(10.dp)) {
                    item { Text("运行日志", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall) }
                    items(runtime.logs.takeLast(12)) { log -> Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                }
            }
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
                        onCheckedChange = { enabled -> viewModel.selectCapabilities(if (enabled) selected + capability.id else selected - capability.id) }
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
        item { NoticeBanner("生成独立项目 APK 的模板组装器仍在 Android 10+ 技术验证中。当前可保存构建计划，不会假装已完成导出。") }
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
        validation.restrictedCapabilities.takeIf { it.isNotEmpty() }?.let { restricted -> item { NoticeBanner("需要 Android 特殊系统流程：${restricted.joinToString()}") } }
        item {
            Button(onClick = { viewModel.updateBuildMetadata(displayName, versionName, packageName); viewModel.prepareBuild() }, modifier = Modifier.fillMaxWidth()) {
                Text("保存本地构建计划")
            }
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
private fun SettingsDialog(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onPickApilot: (Boolean) -> Unit,
    onExportApilot: (AiSettings, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
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
                    Button(
                        onClick = viewModel::checkForUpdate,
                        enabled = state.update !is UpdateUiState.Checking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.update is UpdateUiState.Checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("检查 GitHub 更新")
                    }
                }
                item { UpdateResult(state.update, viewModel::downloadLatestApk) }
                item { HorizontalDivider() }
                item { Text("更新日志", fontWeight = FontWeight.SemiBold) }
                item { Text("0.3.0-alpha：新增粘贴代码工作区、运行控制、更新下载状态与 Apilot V2 互操作。") }
                item { Text("0.2.1-alpha：修复 GitHub Actions 在 Linux Runner 上的 Gradle Wrapper 执行权限。") }
                item { Text("0.2.0-alpha：建立 Kotlin/Compose 创作器、项目工作区、预览桥、能力目录、AI 提示词与 GitHub 更新设置。") }
                item { TextButton(onClick = { showAiSettings = true }) { Text("AI 接口与 Apilot") } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
    if (showAiSettings) {
        AiSettingsDialog(
            initial = viewModel.loadAiSettings(),
            onDismiss = { showAiSettings = false },
            onSave = { settings -> viewModel.saveAiSettings(settings); showAiSettings = false },
            onPickApilot = onPickApilot,
            onExportApilot = onExportApilot
        )
    }
}

@Composable
private fun UpdateResult(update: UpdateUiState, onDownload: () -> Unit) {
    when (update) {
        UpdateUiState.Idle -> Text("点击检查更新以获取最新安装包。", style = MaterialTheme.typography.bodySmall)
        UpdateUiState.Checking -> Text("正在读取 GitHub 最新发布…", style = MaterialTheme.typography.bodySmall)
        is UpdateUiState.UpToDate -> ReleaseNotes(update.release.tagName, update.release.releaseNotes, "当前已是最新版本。")
        is UpdateUiState.DownloadAvailable -> {
            ReleaseNotes(update.release.tagName, update.release.releaseNotes, "发现新版本，可以直接下载。")
            Spacer(Modifier.height(6.dp))
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("下载更新 ${update.release.apkName.orEmpty()}") }
        }
        is UpdateUiState.PackageMissing -> ReleaseNotes(update.release.tagName, update.release.releaseNotes, "发现新版本，但发布方尚未上传 APK 安装包。")
        is UpdateUiState.DownloadQueued -> ReleaseNotes(update.release.tagName, update.release.releaseNotes, "更新正在由系统下载管理器下载。")
        is UpdateUiState.DownloadFinished -> ReleaseNotes(update.release.tagName, update.release.releaseNotes, "下载完成，请在系统下载通知中安装。")
        is UpdateUiState.DownloadFailed -> NoticeBanner(update.message)
        is UpdateUiState.Failed -> NoticeBanner(update.message)
    }
}

@Composable
private fun ReleaseNotes(tagName: String, notes: String, status: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("最新发布：$tagName", fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text(notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AiSettingsDialog(
    initial: AiSettings,
    onDismiss: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onPickApilot: (Boolean) -> Unit,
    onExportApilot: (AiSettings, Boolean) -> Unit
) {
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var providerId by remember { mutableStateOf(initial.providerId) }
    var protocolId by remember { mutableStateOf(initial.protocolId) }
    var requestApiKeyFromApilot by remember { mutableStateOf(false) }
    var exportApiKeyToApilot by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    val currentSettings = AiSettings(endpoint, model, apiKey, providerId, protocolId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 接口与 Apilot") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(onClick = { onPickApilot(requestApiKeyFromApilot) }, modifier = Modifier.fillMaxWidth()) {
                        Text("从 Apilot 选择方案")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("允许从 Apilot 导入 API Key")
                            Text("默认关闭；关闭时只导入地址和模型。", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(requestApiKeyFromApilot, { requestApiKeyFromApilot = it })
                    }
                }
                item { HorizontalDivider() }
                item { OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI-compatible 地址") }) }
                item { OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型") }) }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                item { OutlinedTextField(providerId, { providerId = it }, Modifier.fillMaxWidth(), label = { Text("提供商 ID") }) }
                item { OutlinedTextField(protocolId, { protocolId = it }, Modifier.fillMaxWidth(), label = { Text("协议 ID") }) }
                item {
                    OutlinedButton(onClick = { confirmExport = true }, modifier = Modifier.fillMaxWidth()) { Text("发送当前方案到 Apilot") }
                }
                item { Text("DeepSeek 可使用 https://api.deepseek.com/v1；密钥只保存在本机安全存储。", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(currentSettings) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("发送 API 方案到 Apilot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Apilot 会自行展示导入确认。默认不会发送 API Key。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("同时发送 API Key", modifier = Modifier.weight(1f))
                        Switch(exportApiKeyToApilot, { exportApiKeyToApilot = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onExportApilot(currentSettings, exportApiKeyToApilot)
                    confirmExport = false
                }) { Text("确认发送") }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("取消") } }
        )
    }
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
