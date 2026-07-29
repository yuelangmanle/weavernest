package com.zhique.studio.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.database.Cursor
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.zhique.core.project.CapabilityRegistry
import com.zhique.core.project.ExternalCodeImport
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.PreviewDataPersistence
import com.zhique.core.project.PromptLanguage
import com.zhique.core.stabilization.ApilotAvailability
import com.zhique.core.stabilization.BackNavigationAction
import com.zhique.core.stabilization.BackNavigationPolicy
import com.zhique.core.stabilization.BackNavigationState
import com.zhique.core.stabilization.ProjectArea
import com.zhique.studio.BuildConfig
import com.zhique.studio.BuildUiState
import com.zhique.studio.PreviewRuntimeStatus
import com.zhique.studio.StudioLanguage
import com.zhique.studio.StudioUiState
import com.zhique.studio.StudioViewModel
import com.zhique.studio.TemplateCatalog
import com.zhique.studio.TemplateDefinition
import com.zhique.studio.UpdateUiState
import com.zhique.studio.data.AiSettings
import com.zhique.studio.data.ProjectZipImport
import com.zhique.studio.features.editor.CodeEditorView
import com.zhique.studio.features.editor.CodeEditorAction
import com.zhique.studio.features.editor.CodeEditorCommand
import com.zhique.studio.integrations.AndroidApilotDetector
import com.zhique.studio.integrations.ApilotProfile
import com.zhique.studio.integrations.ApilotV2
import com.zhique.studio.preview.WeaverPreview
import com.zhique.runtime.PreviewDataManager
import com.zhique.core.template.TemplateStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private object StudioRoute {
    const val Home = "home"
    const val Create = "create"
    const val PasteNew = "paste-new"
    const val PasteCurrent = "paste-current"
    const val Templates = "templates"
    const val Project = "project"
    const val ProjectSettings = "project-settings"
    const val Settings = "settings"
    const val ApiSettings = "settings-api"
    const val AiDraft = "ai-draft"
    const val ProjectImportReview = "project-import-review"
    const val RecycleBin = "recycle-bin"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhiqueApp(viewModel: StudioViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: StudioRoute.Home
    var apilotGuidance by remember { mutableStateOf<ApilotAvailability?>(null) }
    var pendingApilotProfile by remember { mutableStateOf<ApilotProfile?>(null) }
    var projectPendingRecycle by remember { mutableStateOf<ProjectDocument?>(null) }
    var projectPendingRename by remember { mutableStateOf<ProjectDocument?>(null) }
    var exitArmed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2_000)
            exitArmed = false
        }
    }

    val navigateBack: () -> Unit = {
        when (
            BackNavigationPolicy.decide(
                BackNavigationState(
                    hasTransientUi = apilotGuidance != null || pendingApilotProfile != null || projectPendingRecycle != null || projectPendingRename != null || state.aiDraft != null || state.pendingProjectImport != null,
                    canNavigateUp = navController.previousBackStackEntry != null,
                    isProjectRoot = currentRoute == StudioRoute.Project,
                    exitArmed = exitArmed
                )
            )
        ) {
            BackNavigationAction.CloseTransientUi -> when {
                apilotGuidance != null -> apilotGuidance = null
                pendingApilotProfile != null -> pendingApilotProfile = null
                projectPendingRecycle != null -> projectPendingRecycle = null
                projectPendingRename != null -> projectPendingRename = null
                state.aiDraft != null -> viewModel.dismissAiDraft()
                state.pendingProjectImport != null -> viewModel.discardPendingProjectImport()
            }
            BackNavigationAction.NavigateUp -> navController.navigateUp()
            BackNavigationAction.ReturnHome -> {
                viewModel.closeProject()
                navController.navigate(StudioRoute.Home) {
                    popUpTo(StudioRoute.Home) { inclusive = false }
                }
            }
            BackNavigationAction.ArmExit -> {
                exitArmed = true
                viewModel.showNotice("再按一次返回退出织雀。")
            }
            BackNavigationAction.ExitApplication -> (context as? Activity)?.finish()
        }
    }
    BackHandler(onBack = navigateBack)

    val apilotPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when {
            result.resultCode == Activity.RESULT_CANCELED -> viewModel.showNotice("已取消从 Apilot 选择 API 方案。")
            result.resultCode == Activity.RESULT_OK && result.data != null -> runCatching {
                ApilotV2.parsePickResult(context, result.data!!)
            }.onSuccess { profile -> pendingApilotProfile = profile }
                .onFailure { viewModel.showNotice(it.message ?: "无法读取 Apilot 返回的方案。") }
            else -> viewModel.showNotice("Apilot 没有返回可用的 API 方案。")
        }
    }
    val launchApilotPicker: (Boolean) -> Unit = { includeApiKey ->
        when (val availability = AndroidApilotDetector.detect(context)) {
            is ApilotAvailability.InstalledCompatible -> runCatching {
                apilotPicker.launch(ApilotV2.createPickIntent(includeApiKey))
            }.onFailure {
                apilotGuidance = ApilotAvailability.LaunchFailed
            }
            else -> apilotGuidance = availability
        }
    }
    val launchApilotExport: (AiSettings, Boolean) -> Unit = { settings, includeApiKey ->
        when (val availability = AndroidApilotDetector.detect(context)) {
            is ApilotAvailability.InstalledCompatible -> runCatching {
                context.startActivity(ApilotV2.createExportIntent(context, settings, includeApiKey))
            }.onFailure { apilotGuidance = ApilotAvailability.LaunchFailed }
            else -> apilotGuidance = availability
        }
    }

    val openProject: () -> Unit = {
        navController.navigate(StudioRoute.Project) {
            popUpTo(StudioRoute.Home) { inclusive = false }
            launchSingleTop = true
        }
    }

    LaunchedEffect(state.aiDraft) {
        if (state.aiDraft != null && currentRoute != StudioRoute.AiDraft) {
            navController.navigate(StudioRoute.AiDraft)
        }
    }

    NavHost(navController = navController, startDestination = StudioRoute.Home) {
        composable(StudioRoute.Home) {
            StudioPage(
                state = state,
                title = "织雀",
                onToggleLanguage = viewModel::toggleLanguage,
                onSettings = { navController.navigate(StudioRoute.Settings) }
            ) { modifier ->
                HomeScreen(
                    state = state,
                    onCreate = { navController.navigate(StudioRoute.Create) },
                    onPaste = { navController.navigate(StudioRoute.PasteNew) },
                    onTemplates = { navController.navigate(StudioRoute.Templates) },
                    onOpenRecycleBin = { navController.navigate(StudioRoute.RecycleBin) },
                    onOpenProject = { projectId ->
                        viewModel.selectProject(projectId)
                        openProject()
                    },
                    onDuplicateProject = viewModel::duplicateProject,
                    onExportProject = viewModel::exportProject,
                    onRequestRename = { project -> projectPendingRename = project },
                    onRequestRecycle = { project -> projectPendingRecycle = project },
                    modifier = modifier
                )
            }
        }
        composable(StudioRoute.Create) {
            CreateProjectScreen(
                onBack = navigateBack,
                onCreate = { name ->
                    viewModel.createBlankProject(name)
                    openProject()
                }
            )
        }
        composable(StudioRoute.Templates) {
            TemplateCenterScreen(
                onBack = navigateBack,
                onChoose = { template ->
                    viewModel.createFromTemplate(template)
                    openProject()
                }
            )
        }
        composable(StudioRoute.PasteNew) {
            PasteCodeScreen(
                state = state,
                replaceCurrentProject = false,
                onBack = {
                    viewModel.discardZipImport()
                    navigateBack()
                },
                onCommit = { name, source ->
                    viewModel.createFromPastedCode(name, source)
                    openProject()
                },
                onReviewZip = viewModel::inspectZip,
                onImportError = viewModel::showNotice,
                onCommitZip = {
                    if (viewModel.commitZipImport()) openProject()
                }
            )
        }
        composable(StudioRoute.PasteCurrent) {
            PasteCodeScreen(
                state = state,
                replaceCurrentProject = true,
                onBack = navigateBack,
                onCommit = { _, source ->
                    if (viewModel.pasteIntoSelectedProject(source)) navController.navigate(StudioRoute.ProjectImportReview)
                },
                onReviewZip = { _, _ -> },
                onImportError = viewModel::showNotice,
                onCommitZip = {}
            )
        }
        composable(StudioRoute.Project) {
            val document = state.selectedDocument
            if (document == null) {
                LaunchedEffect(Unit) { navController.navigateUp() }
            } else {
                StudioPage(
                    state = state,
                    title = document.metadata.displayName,
                    onBack = navigateBack,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onSettings = { navController.navigate(StudioRoute.ProjectSettings) }
                ) { modifier ->
                    ProjectWorkspace(
                        state = state,
                        document = document,
                        viewModel = viewModel,
                        onPasteIntoProject = { navController.navigate(StudioRoute.PasteCurrent) },
                        modifier = modifier
                    )
                }
            }
        }
        composable(StudioRoute.Settings) {
            SettingsScreen(
                state = state,
                viewModel = viewModel,
                onBack = navigateBack,
                onOpenApiSettings = { navController.navigate(StudioRoute.ApiSettings) }
            )
        }
        composable(StudioRoute.ApiSettings) {
            ApiSettingsScreen(
                initial = viewModel.loadAiSettings(),
                onBack = navigateBack,
                onSave = viewModel::saveAiSettings,
                onPickApilot = launchApilotPicker,
                onExportApilot = launchApilotExport
            )
        }
        composable(StudioRoute.ProjectSettings) {
            val document = state.selectedDocument
            if (document == null) {
                LaunchedEffect(Unit) { navController.navigateUp() }
            } else {
                ProjectSettingsScreen(document, state.projectSnapshots, viewModel, navigateBack)
            }
        }
        composable(StudioRoute.AiDraft) {
            val document = state.selectedDocument
            val draft = state.aiDraft
            if (document == null || draft == null) {
                LaunchedEffect(Unit) { navController.navigateUp() }
            } else {
                AiDraftReviewScreen(
                    document = document,
                    draft = draft,
                    onBack = {
                        viewModel.dismissAiDraft()
                        navController.navigateUp()
                    },
                    onApply = {
                        viewModel.applyAiDraftToIndex()
                        navController.navigateUp()
                    }
                )
            }
        }
        composable(StudioRoute.ProjectImportReview) {
            val document = state.selectedDocument
            val draft = state.pendingProjectImport
            if (document == null || draft == null) {
                LaunchedEffect(Unit) { navController.navigateUp() }
            } else {
                ProjectImportReviewScreen(
                    document = document,
                    draft = draft,
                    onBack = {
                        viewModel.discardPendingProjectImport()
                        navController.navigateUp()
                    },
                    onApply = {
                        if (viewModel.commitPendingProjectImport()) navController.popBackStack(StudioRoute.Project, inclusive = false)
                    }
                )
            }
        }
        composable(StudioRoute.RecycleBin) {
            RecycleBinScreen(
                state = state,
                onBack = navigateBack,
                onRestore = viewModel::restoreProjectFromRecycleBin
            )
        }
    }
    pendingApilotProfile?.let { profile ->
        ApilotImportReviewDialog(
            profile = profile,
            onConfirm = {
                viewModel.applyApilotProfile(profile)
                pendingApilotProfile = null
            },
            onDismiss = { pendingApilotProfile = null }
        )
    }
    apilotGuidance?.let { availability ->
        ApilotGuidanceDialog(
            availability = availability,
            onDismiss = { apilotGuidance = null },
            onOpenRepository = {
                apilotGuidance = null
                uriHandler.openUri(ApilotV2.repositoryUrl)
            },
            onOpenApplication = {
                if (!AndroidApilotDetector.launchApplication(context)) {
                    apilotGuidance = ApilotAvailability.LaunchFailed
                } else {
                    apilotGuidance = null
                }
            },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ApilotV2.packageName}"))
                )
                apilotGuidance = null
            }
        )
    }
    projectPendingRecycle?.let { project ->
        AlertDialog(
            onDismissRequest = { projectPendingRecycle = null },
            title = { Text("移入回收站？") },
            text = { Text("“${project.metadata.displayName}”将从项目列表移入回收站。代码、资源和本地快照不会立即删除，可在回收站恢复。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveProjectToRecycleBin(project.metadata.id)
                    projectPendingRecycle = null
                }) { Text("移入回收站") }
            },
            dismissButton = { TextButton(onClick = { projectPendingRecycle = null }) { Text("取消") } }
        )
    }
    projectPendingRename?.let { project ->
        var name by remember(project.metadata.id) { mutableStateOf(project.metadata.displayName) }
        AlertDialog(
            onDismissRequest = { projectPendingRename = null },
            title = { Text("重命名项目") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("项目名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProject(project.metadata.id, name)
                        projectPendingRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { projectPendingRename = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioPage(
    state: StudioUiState,
    title: String,
    onToggleLanguage: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    onBack?.let { navigateUp ->
                        IconButton(onClick = navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t(state, "返回", "Back"))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleLanguage) {
                        Icon(Icons.Default.Translate, contentDescription = t(state, "切换语言", "Change language"))
                    }
                    onSettings?.let { openSettings ->
                        IconButton(onClick = openSettings) {
                            Icon(Icons.Default.Settings, contentDescription = t(state, "设置", "Settings"))
                        }
                    }
                }
            )
        }
    ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProjectScreen(onBack: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建项目") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("从一个可运行的空白项目开始", style = MaterialTheme.typography.headlineSmall)
            Text("项目创建后可在创作区粘贴外部代码、与 AI 对话或编辑文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("项目名称") },
                supportingText = { Text("名称可在发布前修改。") },
                singleLine = true
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { onCreate(name.ifBlank { "未命名工具" }) }, modifier = Modifier.fillMaxWidth()) {
                Text("创建并打开项目")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCenterScreen(onBack: () -> Unit, onChoose: (TemplateDefinition) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("从经过标注的起点开始", style = MaterialTheme.typography.headlineSmall)
                Text("模板只会标注已实现或明确受系统限制的能力；不会把占位功能包装成可用能力。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(TemplateCatalog.visible, key = { it.id }) { template ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onChoose(template) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            template.category + " · Android " + template.minimumApi + "+ · " + if (template.status == TemplateStatus.Experimental) "实验性" else "可用",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (template.status == TemplateStatus.Experimental) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                        Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (template.verificationScenario.isNotBlank()) {
                            Text("验收：" + template.verificationScenario, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onOpenApiSettings: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val localChangelog = remember {
        runCatching {
            context.assets.open("changelog.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("本地更新日志不可用。")
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t(state, "返回", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleLanguage) {
                        Icon(Icons.Default.Translate, contentDescription = t(state, "切换语言", "Change language"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("应用与更新", style = MaterialTheme.typography.titleMedium) }
            item { Text("织雀 ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold) }
            item {
                Text(
                    "GitHub：github.com/${BuildConfig.GITHUB_REPOSITORY}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri("https://github.com/${BuildConfig.GITHUB_REPOSITORY}") }
                )
            }
            item { Text("开发人员：月亮满了") }
            item { Text("QQ：3335196397") }
            item { HorizontalDivider() }
            item {
                Button(
                    onClick = viewModel::checkForUpdate,
                    enabled = state.update !is UpdateUiState.Checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.update is UpdateUiState.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("检查更新")
                    }
                }
            }
            item { UpdateResult(state.update, viewModel::downloadLatestApk) }
            item { HorizontalDivider() }
            item { Text("AI 与 API", style = MaterialTheme.typography.titleMedium) }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpenApiSettings() }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AI 接口与 Apilot", fontWeight = FontWeight.SemiBold)
                        Text("管理 DeepSeek、OpenAI-compatible 接口，以及与 Apilot V2 的确认式导入和导出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { HorizontalDivider() }
            item { Text("更新日志", style = MaterialTheme.typography.titleMedium) }
            item { Text(localChangelog, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiSettingsScreen(
    initial: AiSettings,
    onBack: () -> Unit,
    onSave: (AiSettings) -> Boolean,
    onPickApilot: (Boolean) -> Unit,
    onExportApilot: (AiSettings, Boolean) -> Unit
) {
    var endpoint by rememberSaveable(initial.endpoint) { mutableStateOf(initial.endpoint) }
    var model by rememberSaveable(initial.model) { mutableStateOf(initial.model) }
    // API keys must never be copied into the Activity saved-state Bundle.
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var providerId by rememberSaveable(initial.providerId) { mutableStateOf(initial.providerId) }
    var protocolId by rememberSaveable(initial.protocolId) { mutableStateOf(initial.protocolId) }
    var requestApiKeyFromApilot by rememberSaveable { mutableStateOf(false) }
    var exportApiKeyToApilot by rememberSaveable { mutableStateOf(false) }
    var confirmExport by rememberSaveable { mutableStateOf(false) }
    val settings = AiSettings(endpoint, model, apiKey, providerId, protocolId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 接口与 Apilot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("接口凭据只保存于本机加密存储。导入和导出始终需要用户主动确认。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedButton(onClick = { onPickApilot(requestApiKeyFromApilot) }, modifier = Modifier.fillMaxWidth()) {
                    Text("从 Apilot 选择方案")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("允许导入 API Key", fontWeight = FontWeight.SemiBold)
                        Text("默认关闭。关闭时 Apilot 只提供连接地址和默认模型。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = requestApiKeyFromApilot, onCheckedChange = { requestApiKeyFromApilot = it })
                }
            }
            item { HorizontalDivider() }
            item { OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI-compatible 地址") }, singleLine = true) }
            item { OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型") }, singleLine = true) }
            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
            item { OutlinedTextField(providerId, { providerId = it }, Modifier.fillMaxWidth(), label = { Text("提供商 ID") }, singleLine = true) }
            item { OutlinedTextField(protocolId, { protocolId = it }, Modifier.fillMaxWidth(), label = { Text("协议 ID") }, singleLine = true) }
            item {
                OutlinedButton(onClick = { confirmExport = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("发送当前方案到 Apilot")
                }
            }
            item {
                Button(
                    onClick = { if (onSave(settings)) onBack() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存接口设置") }
            }
        }
    }
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("发送 API 方案到 Apilot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("默认只发送地址、模型和协议，不会发送 API Key。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("同时发送 API Key", modifier = Modifier.weight(1f))
                        Switch(checked = exportApiKeyToApilot, onCheckedChange = { exportApiKeyToApilot = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onExportApilot(settings, exportApiKeyToApilot)
                    confirmExport = false
                }) { Text("确认发送") }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiDraftReviewScreen(
    document: ProjectDocument,
    draft: String,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    val original = document.files["index.html"].orEmpty()
    val differences = remember(original, draft) { lineDiff(original, draft) }
    val previewDocument = remember(document, draft) {
        document.copy(
            metadata = document.metadata.copy(
                id = "${document.metadata.id}-draft",
                previewDataPersistence = PreviewDataPersistence.Ephemeral
            ),
            files = document.files + ("index.html" to draft)
        )
    }
    var previewLogs by remember(draft) { mutableStateOf(emptyList<String>()) }
    val added = differences.count { difference -> difference.kind == '+' }
    val removed = differences.count { difference -> difference.kind == '-' }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("审核 AI 草案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "放弃草案")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("index.html 变更", fontWeight = FontWeight.SemiBold)
                    Text("新增 $added 行，移除 $removed 行。下方预览不会写入正式项目。", style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(156.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(differences.take(160)) { difference ->
                    val color = when (difference.kind) {
                        '+' -> MaterialTheme.colorScheme.primary
                        '-' -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        "${difference.kind} ${difference.text}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = color,
                        maxLines = 1
                    )
                }
            }
            Text("草案预览", style = MaterialTheme.typography.titleSmall)
            Surface(modifier = Modifier.fillMaxWidth().weight(1f), tonalElevation = 1.dp) {
                WeaverPreview(
                    document = previewDocument,
                    runToken = 1L,
                    isRunning = true,
                    onReady = {},
                    onLog = { message, isError ->
                        val prefix = if (isError) "错误：" else ""
                        previewLogs = (previewLogs + "$prefix$message").takeLast(3)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            previewLogs.lastOrNull()?.let { message -> NoticeBanner(message) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("放弃") }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("确认写入项目") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectImportReviewScreen(
    document: ProjectDocument,
    draft: com.zhique.core.project.ExternalCodeDraft,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    val previewDocument = remember(document, draft) {
        document.copy(
            files = document.files + draft.files,
            metadata = document.metadata.copy(previewDataPersistence = PreviewDataPersistence.Ephemeral)
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("确认外部代码改动") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { NoticeBanner("确认后才会写入项目。审核预览使用临时数据，不会覆盖当前项目的预览数据。") }
            item { ImportAnalysisSummary(draft.analysis) }
            draft.files.toSortedMap().forEach { (path, after) ->
                val before = document.files[path].orEmpty()
                val differences = lineDiff(before, after)
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(path, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (path in document.files) "替换 ${before.lines().size} 行为 ${after.lines().size} 行" else "新增 ${after.lines().size} 行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            differences.filter { it.kind != ' ' }.take(32).forEach { difference ->
                                Text("${difference.kind} ${difference.text}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Surface(Modifier.fillMaxWidth().height(300.dp), tonalElevation = 1.dp) {
                    WeaverPreview(
                        document = previewDocument,
                        runToken = 1L,
                        isRunning = true,
                        onReady = {},
                        onLog = { _, _ -> },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            item {
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("确认写入项目") }
            }
            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
}

private data class DiffLine(val kind: Char, val text: String)

private fun lineDiff(before: String, after: String): List<DiffLine> {
    val left = before.lines()
    val right = after.lines()
    if (left.size > 240 || right.size > 240) {
        return listOf(
            DiffLine('~', "文件超过 240 行，已显示变更概览。"),
            DiffLine('-', "原文件：${left.size} 行"),
            DiffLine('+', "AI 草案：${right.size} 行")
        )
    }
    val table = Array(left.size + 1) { IntArray(right.size + 1) }
    for (leftIndex in left.indices.reversed()) {
        for (rightIndex in right.indices.reversed()) {
            table[leftIndex][rightIndex] = if (left[leftIndex] == right[rightIndex]) {
                table[leftIndex + 1][rightIndex + 1] + 1
            } else {
                maxOf(table[leftIndex + 1][rightIndex], table[leftIndex][rightIndex + 1])
            }
        }
    }
    val result = mutableListOf<DiffLine>()
    var leftIndex = 0
    var rightIndex = 0
    while (leftIndex < left.size || rightIndex < right.size) {
        when {
            leftIndex == left.size -> result += DiffLine('+', right[rightIndex++])
            rightIndex == right.size -> result += DiffLine('-', left[leftIndex++])
            left[leftIndex] == right[rightIndex] -> {
                result += DiffLine(' ', left[leftIndex])
                leftIndex += 1
                rightIndex += 1
            }
            table[leftIndex + 1][rightIndex] >= table[leftIndex][rightIndex + 1] -> result += DiffLine('-', left[leftIndex++])
            else -> result += DiffLine('+', right[rightIndex++])
        }
    }
    return result
}

@Composable
private fun HomeScreen(
    state: StudioUiState,
    onCreate: () -> Unit,
    onPaste: () -> Unit,
    onTemplates: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onExportProject: (String) -> Unit,
    onRequestRename: (ProjectDocument) -> Unit,
    onRequestRecycle: (ProjectDocument) -> Unit,
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
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t(state, "我的项目", "My projects"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.recycledProjects.isNotEmpty()) {
                    TextButton(onClick = onOpenRecycleBin) { Text("回收站（${state.recycledProjects.size}）") }
                }
            }
        }
        if (state.documents.isEmpty()) {
            item { NoticeBanner("还没有项目。粘贴一段 HTML、CSS 或 JavaScript，然后点击创建并打开编辑器。") }
        }
        items(state.documents, key = { it.metadata.id }) { document ->
            var menuOpen by rememberSaveable(document.metadata.id) { mutableStateOf(false) }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                    Column(
                        Modifier.weight(1f).clickable { onOpenProject(document.metadata.id) }
                    ) {
                        Text(document.metadata.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("${document.metadata.packageName} · ${document.metadata.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val modified = document.metadata.lastModifiedEpochMillis
                        Text(
                            if (modified > 0L) "修改于：${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(modified))}" else "修改时间未知",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val latestBuild = document.buildHistory.lastOrNull()
                        Text(
                            buildString {
                                append(latestBuild?.let { "最近构建：${it.status} · ${it.versionName}" } ?: "尚未构建")
                                if (document.metadata.id == state.selectedProjectId) append(" · ${previewStatusLabel(state.previewRuntime.status)}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "项目操作")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("打开") },
                                onClick = { menuOpen = false; onOpenProject(document.metadata.id) }
                            )
                            DropdownMenuItem(
                                text = { Text("复制为新项目") },
                                onClick = { menuOpen = false; onDuplicateProject(document.metadata.id) }
                            )
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                onClick = { menuOpen = false; onRequestRename(document) }
                            )
                            DropdownMenuItem(
                                text = { Text("导出项目 ZIP") },
                                onClick = { menuOpen = false; onExportProject(document.metadata.id) }
                            )
                            DropdownMenuItem(
                                text = { Text("移入回收站") },
                                onClick = { menuOpen = false; onRequestRecycle(document) }
                            )
                        }
                    }
                }
            }
        }
        state.notice?.let { notice -> item { NoticeBanner(notice) } }
    }
}

private fun previewStatusLabel(status: PreviewRuntimeStatus): String = when (status) {
    PreviewRuntimeStatus.Idle -> "未运行"
    PreviewRuntimeStatus.Running -> "运行中"
    PreviewRuntimeStatus.Stopped -> "已停止"
    PreviewRuntimeStatus.Error -> "运行错误"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecycleBinScreen(
    state: StudioUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("已移入回收站的项目不会立即删除，可恢复到原来的独立项目身份。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.recycledProjects.isEmpty()) {
                item { NoticeBanner("回收站为空。") }
            }
            items(state.recycledProjects, key = { it.recycleId }) { recycled ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(recycled.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("删除时间：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(recycled.deletedAtEpochMillis))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { onRestore(recycled.recycleId) }) { Text("恢复项目") }
                    }
                }
            }
            state.notice?.let { notice -> item { NoticeBanner(notice) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSettingsScreen(
    document: ProjectDocument,
    snapshots: List<com.zhique.studio.data.ProjectSnapshot>,
    viewModel: StudioViewModel,
    onBack: () -> Unit
) {
    var snapshotPendingRestore by remember { mutableStateOf<com.zhique.studio.data.ProjectSnapshot?>(null) }
    LaunchedEffect(document.metadata.id) { viewModel.refreshProjectSnapshots(document.metadata.id) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("项目设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "数据只作用于“${document.metadata.displayName}”的预览环境；不会进入分享的项目 ZIP 或生成 APK。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { DataPanel(document, viewModel, Modifier.fillMaxWidth()) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("代码快照", style = MaterialTheme.typography.titleMedium)
                    Text("每次自动保存前会保留最近 30 个工作区快照。恢复会先保存当前版本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (snapshots.isEmpty()) {
                item { Text("尚无可恢复快照。", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
            }
            items(snapshots, key = { it.snapshotId }) { snapshot ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(snapshot.createdAtEpochMillis)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { snapshotPendingRestore = snapshot }) { Text("恢复") }
                }
            }
        }
    }
    snapshotPendingRestore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { snapshotPendingRestore = null },
            title = { Text("恢复代码快照？") },
            text = { Text("当前代码会先保存为一个新快照，然后恢复所选版本。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreProjectSnapshot(document.metadata.id, snapshot.snapshotId)
                    snapshotPendingRestore = null
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { snapshotPendingRestore = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteCodeScreen(
    state: StudioUiState,
    replaceCurrentProject: Boolean,
    onBack: () -> Unit,
    onCommit: (String, String) -> Unit,
    onReviewZip: (String, ByteArray) -> Unit,
    onImportError: (String) -> Unit,
    onCommitZip: () -> Unit
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
            val limit = if (name.endsWith(".zip", true)) ProjectZipImport.maxArchiveBytes else 2 * 1024 * 1024
            val bytes = runCatching { stream.readBounded(limit) }.getOrElse { error ->
                onImportError(error.message ?: "导入文件无法读取。")
                return@use
            }
            if (name.endsWith(".zip", true)) onReviewZip(name, bytes)
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
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
                    onClick = {
                        val types = if (replaceCurrentProject) {
                            arrayOf("text/html", "text/css", "text/javascript", "application/json", "text/plain")
                        } else {
                            arrayOf("text/html", "text/css", "text/javascript", "application/json", "application/zip", "text/plain")
                        }
                        importLauncher.launch(types)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (replaceCurrentProject) "导入文件" else "导入文件 / ZIP") }
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
                        ImportAnalysisSummary(it.analysis)
                    }
                }
            }
            state.pendingZipImport?.takeIf { !replaceCurrentProject }?.let { review ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("ZIP 导入检查", fontWeight = FontWeight.SemiBold)
                        Text("${review.textFiles.size} 个代码文件，${review.binaryAssets.size} 个资源文件", style = MaterialTheme.typography.bodySmall)
                        Text("文件：${review.fileNames.take(8).joinToString()}${if (review.fileNames.size > 8) " 等" else ""}", style = MaterialTheme.typography.bodySmall)
                        ImportAnalysisSummary(review.analysis)
                    }
                }
                Button(onClick = onCommitZip, modifier = Modifier.fillMaxWidth()) { Text("确认创建 ZIP 项目") }
            } ?: Button(
                onClick = { onCommit(projectName, source) },
                enabled = source.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (replaceCurrentProject) "写入当前项目并打开编辑器" else "创建项目并打开编辑器") }
        }
    }
}

@Composable
private fun ImportAnalysisSummary(analysis: com.zhique.core.project.ImportAnalysis) {
    if (analysis.suggestedCapabilities.isNotEmpty()) {
        Text("建议启用：${analysis.suggestedCapabilities.sorted().joinToString()}", style = MaterialTheme.typography.bodySmall)
    }
    analysis.suggestions.forEach { suggestion ->
        Text("${suggestion.fileName}：建议改为 ${suggestion.replacement}", style = MaterialTheme.typography.bodySmall)
    }
    if (analysis.unknownRuntimeMethods.isNotEmpty()) {
        Text(
            "未知织雀 API：${analysis.unknownRuntimeMethods.joinToString { it.method }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (analysis.unavailableRuntimeMethods.isNotEmpty()) {
        Text(
            "当前运行时尚未实现：${analysis.unavailableRuntimeMethods.joinToString { it.method }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (analysis.unknownDeclaredCapabilities.isNotEmpty()) {
        Text(
            "未知能力声明：${analysis.unknownDeclaredCapabilities.sorted().joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    analysis.manifestErrors.forEach { error ->
        Text(
            "${error.fileName}：${error.message}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
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
    BoxWithConstraints(modifier) {
        val useNavigationRail = maxWidth >= 720.dp
        if (useNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    ProjectArea.entries.forEach { area ->
                        NavigationRailItem(
                            selected = state.selectedTab == area,
                            onClick = { viewModel.selectTab(area) },
                            icon = { ProjectAreaIcon(area) },
                            label = { Text(projectAreaLabel(state, area)) }
                        )
                    }
                }
                ProjectWorkspaceContent(
                    state = state,
                    document = document,
                    viewModel = viewModel,
                    onPasteIntoProject = onPasteIntoProject,
                    showBottomNavigation = false,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            ProjectWorkspaceContent(
                state = state,
                document = document,
                viewModel = viewModel,
                onPasteIntoProject = onPasteIntoProject,
                showBottomNavigation = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ProjectWorkspaceContent(
    state: StudioUiState,
    document: ProjectDocument,
    viewModel: StudioViewModel,
    onPasteIntoProject: () -> Unit,
    showBottomNavigation: Boolean,
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
        when (state.selectedTab) {
            ProjectArea.Create -> CreatePanel(state, document, viewModel, Modifier.weight(1f))
            ProjectArea.Run -> PreviewPanel(state, document, viewModel, Modifier.weight(1f))
            ProjectArea.Capabilities -> CapabilitiesPanel(document, viewModel, Modifier.weight(1f))
            ProjectArea.Publish -> BuildPanel(document, viewModel, Modifier.weight(1f))
        }
        state.notice?.let { NoticeBanner(it, Modifier.padding(12.dp)) }
        if (showBottomNavigation) {
            NavigationBar {
                ProjectArea.entries.forEach { area ->
                    NavigationBarItem(
                        selected = state.selectedTab == area,
                        onClick = { viewModel.selectTab(area) },
                        icon = { ProjectAreaIcon(area) },
                        label = { Text(projectAreaLabel(state, area)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectAreaIcon(area: ProjectArea) {
    when (area) {
        ProjectArea.Create -> Icon(Icons.Default.Edit, contentDescription = null)
        ProjectArea.Run -> Icon(Icons.Default.PlayArrow, contentDescription = null)
        ProjectArea.Capabilities -> Icon(Icons.Default.Tune, contentDescription = null)
        ProjectArea.Publish -> Icon(Icons.Default.Publish, contentDescription = null)
    }
}

private fun projectAreaLabel(state: StudioUiState, area: ProjectArea): String = if (state.language == StudioLanguage.Chinese) area.chinese else area.english

@Composable
private fun CreatePanel(state: StudioUiState, document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    var mode by rememberSaveable(document.metadata.id) { mutableStateOf(0) }
    Column(modifier) {
        TabRow(selectedTabIndex = mode) {
            Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("AI") })
            Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("文件") })
        }
        when (mode) {
            0 -> AiPanel(state, document, viewModel, Modifier.weight(1f))
            else -> FilesPanel(document, viewModel, Modifier.weight(1f))
        }
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
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(viewModel.copyExternalPrompt(PromptLanguage.ZhCn))) },
                    modifier = Modifier.weight(1f)
                ) { Text("复制中文提示词") }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(viewModel.copyExternalPrompt(PromptLanguage.En))) },
                    modifier = Modifier.weight(1f)
                ) { Text("Copy English prompt") }
            }
        }
        state.importAnalysis?.let { analysis ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("导入兼容性检查", fontWeight = FontWeight.SemiBold)
                        if (analysis.suggestions.isEmpty() && analysis.unknownRuntimeMethods.isEmpty() && analysis.unavailableRuntimeMethods.isEmpty() && analysis.unknownDeclaredCapabilities.isEmpty() && analysis.manifestErrors.isEmpty()) {
                            Text("未发现需要转换或修正的设备 API。")
                        }
                        ImportAnalysisSummary(analysis)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    var selectedFile by rememberSaveable(document.metadata.id) { mutableStateOf(document.files.keys.firstOrNull() ?: "index.html") }
    var commandId by rememberSaveable(document.metadata.id) { mutableStateOf(0L) }
    var editorCommand by remember(document.metadata.id) { mutableStateOf<CodeEditorCommand?>(null) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                commandId += 1
                editorCommand = CodeEditorCommand(commandId, CodeEditorAction.Undo)
            }) { Text("撤销") }
            TextButton(onClick = {
                commandId += 1
                editorCommand = CodeEditorCommand(commandId, CodeEditorAction.Redo)
            }) { Text("重做") }
            TextButton(onClick = {
                commandId += 1
                editorCommand = CodeEditorCommand(commandId, CodeEditorAction.FindReplace)
            }) { Text("查找 / 替换") }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            CodeEditorView(
                path = selectedFile,
                content = selectedContent,
                onContentChange = { viewModel.updateFile(selectedFile, it) },
                command = editorCommand,
                modifier = Modifier.fillMaxSize()
            )
        }
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
        Text(
            if (document.metadata.previewDataPersistence == PreviewDataPersistence.Persistent) {
                "预览数据会在本项目内持续保存；导出 APK 时默认不会携带。"
            } else {
                "此项目关闭了预览数据持久化；下次运行前会清除普通预览数据。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("运行日志", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        if (runtime.status == PreviewRuntimeStatus.Error) {
                            TextButton(onClick = viewModel::requestAiRuntimeRepair, enabled = !state.isAiRequestRunning) { Text("让 AI 修复") }
                        }
                    }
                    LazyColumn(Modifier.height(90.dp)) {
                        items(runtime.logs.takeLast(12)) { log -> Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilitiesPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val selected = document.metadata.capabilities
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("能力与权限", style = MaterialTheme.typography.titleMedium)
            Text("导出的 APK 只声明此处选择的权限；系统仍会在实际使用时询问用户。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedButton(
                onClick = { TemplateCatalog.visible.firstOrNull { template -> template.id == "device-capability-diagnostic" }?.let(viewModel::createFromTemplate) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("新建设备能力诊断项目") }
        }
        items(CapabilityRegistry.all(), key = { it.id }) { capability ->
            val status = capabilityPresentation(context, capability)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(capability.title, fontWeight = FontWeight.SemiBold)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun capabilityPresentation(context: Context, capability: com.zhique.core.project.CapabilityDefinition): String {
    val hardwareAvailable = when (capability.id) {
        "camera" -> context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        "bluetooth_le" -> context.getSystemService(BluetoothManager::class.java)?.adapter != null
        "nfc" -> NfcAdapter.getDefaultAdapter(context) != null
        "usb" -> context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        else -> true
    }
    if (!hardwareAvailable) return "此设备不支持"
    if (capability.requiresSpecialSystemFlow) return "实际使用时需要 Android 系统确认或设置流程"
    if (capability.manifestPermissions.isEmpty()) return "不需要危险权限；仅在用户操作时调用"
    val granted = capability.manifestPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    return if (granted) "Android 权限已授权" else "将在实际调用或诊断测试时请求权限"
}

@Composable
private fun BuildPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    val context = LocalContext.current
    var displayName by rememberSaveable(document.metadata.id, document.metadata.displayName) { mutableStateOf(document.metadata.displayName) }
    var versionName by rememberSaveable(document.metadata.id, document.metadata.versionName) { mutableStateOf(document.metadata.versionName) }
    var packageName by rememberSaveable(document.metadata.id, document.metadata.packageName) { mutableStateOf(document.metadata.packageName) }
    var backupPassword by remember(document.metadata.id) { mutableStateOf("") }
    val buildState = viewModel.state.build
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.use(::readBoundedIcon) ?: error("无法读取所选图标。") }
                .onSuccess(viewModel::updateProjectIcon)
                .onFailure { error -> viewModel.showNotice(error.message ?: "无法读取所选图标。") }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.use(::readBoundedSigningBackup) ?: error("无法读取签名备份。") }
                .onSuccess { bytes -> viewModel.restoreProjectSigningBackup(bytes, backupPassword) }
                .onFailure { error -> viewModel.showNotice(error.message ?: "无法读取签名备份。") }
        }
    }
    val validation = CapabilityRegistry.validate(document.metadata.capabilities)
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("APK 构建", style = MaterialTheme.typography.titleMedium) }
        item { NoticeBanner("APK 会从织雀内置运行时模板组装；只有签名与 v2/v3 验证成功后才会锁定包名并递增内部版本号。") }
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
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.exportProjectSigningBackup(backupPassword) },
                    enabled = backupPassword.length >= 12,
                    modifier = Modifier.weight(1f)
                ) { Text("导出签名备份") }
                OutlinedButton(
                    onClick = { backupPicker.launch(arrayOf("application/vnd.zhique.signing-key-backup", "application/octet-stream")) },
                    enabled = backupPassword.length >= 12,
                    modifier = Modifier.weight(1f)
                ) { Text("恢复签名备份") }
            }
        }
        item { Text("内部版本号：${document.metadata.versionCode}，每次成功导出自动递增。") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("应用图标", fontWeight = FontWeight.SemiBold)
                    Text(
                        document.metadata.iconAssetPath?.let { "已选择项目 PNG 图标。" } ?: "使用织雀默认项目图标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { iconPicker.launch("image/png") }) { Text("选择 PNG") }
            }
        }
        document.metadata.iconAssetPath?.let {
            item { TextButton(onClick = viewModel::clearProjectIcon) { Text("恢复默认图标") } }
        }
        item { Text("Manifest 权限：${validation.manifestPermissions.joinToString().ifBlank { "无" }}", style = MaterialTheme.typography.bodySmall) }
        validation.restrictedCapabilities.takeIf { it.isNotEmpty() }?.let { restricted -> item { NoticeBanner("需要 Android 特殊系统流程：${restricted.joinToString()}") } }
        item {
            Button(onClick = { viewModel.updateBuildMetadata(displayName, versionName, packageName); viewModel.prepareBuild() }, modifier = Modifier.fillMaxWidth()) {
                Text("检查构建条件")
            }
        }
        item {
            OutlinedTextField(
                value = backupPassword,
                onValueChange = { backupPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (document.metadata.signingBackupId == null) "项目密钥备份密码" else "新的密钥备份密码（可留空）") },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        if (document.metadata.signingBackupId == null) {
                            "首次导出必须设置至少 12 位密码，用于加密备份本项目的签名密钥。"
                        } else {
                            "已有加密密钥备份；仅在需要重新生成备份时填写。"
                        }
                    )
                }
            )
        }
        item {
            Button(
                onClick = {
                    viewModel.buildApk(backupPassword)
                    backupPassword = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = buildState !is BuildUiState.Building
            ) {
                Text(if (buildState is BuildUiState.Building) "正在构建 APK" else "构建并签名 APK")
            }
        }
        when (buildState) {
            BuildUiState.Idle -> Unit
            is BuildUiState.Validated -> item { Text("检查通过，候选内部版本号：${buildState.candidateVersionCode}", style = MaterialTheme.typography.bodySmall) }
            BuildUiState.Building -> item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("正在写入资源、签名并校验…") } }
            is BuildUiState.Succeeded -> item { NoticeBanner("构建完成：${buildState.artifactName}") }
            is BuildUiState.Failed -> item { NoticeBanner("构建未完成：${buildState.message}") }
        }
        document.buildHistory.lastOrNull { record -> record.status == "succeeded" && record.artifactFileName != null }?.let { record ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::installLatestBuiltApk, modifier = Modifier.weight(1f)) { Text("安装 ${record.artifactFileName}") }
                    OutlinedButton(onClick = viewModel::shareLatestBuiltApk, modifier = Modifier.weight(1f)) { Text("发送 APK") }
                }
            }
        }
        items(document.buildHistory.reversed()) { record ->
            Text("${record.versionName} (${record.versionCode}) · ${record.status} · ${record.message}${record.artifactFileName?.let { "\n$it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun readBoundedIcon(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= 2_000_000) { "图标文件超过 2MB 限制。" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun readBoundedSigningBackup(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= 320 * 1024) { "签名备份文件超过允许大小。" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@Composable
private fun DataPanel(document: ProjectDocument, viewModel: StudioViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataManager = remember(context.applicationContext) { PreviewDataManager(context) }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupAction by remember { mutableStateOf<PreviewBackupAction?>(null) }
    var backupPassword by rememberSaveable { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.zhique.runtime-data-backup")
    ) { uri ->
        if (uri == null) backupStatus = "已取消选择备份位置。" else {
            backupUri = uri
            backupAction = PreviewBackupAction.Export
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) backupStatus = "已取消选择备份文件。" else {
            backupUri = uri
            backupAction = PreviewBackupAction.Import
        }
    }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("项目数据", style = MaterialTheme.typography.titleMedium)
        Text("预览数据属于 ${document.metadata.displayName}。生成 APK 后，应用会使用独立的数据沙箱。")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("预览数据持久化", fontWeight = FontWeight.SemiBold)
                Text(
                    if (document.metadata.previewDataPersistence == PreviewDataPersistence.Persistent) {
                        "重启或再次打开预览后保留本项目的普通数据。"
                    } else {
                        "每次开始预览前清除普通数据；私密运行时配置不会被删除。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = document.metadata.previewDataPersistence == PreviewDataPersistence.Persistent,
                onCheckedChange = { enabled ->
                    viewModel.updatePreviewDataPersistence(
                        if (enabled) PreviewDataPersistence.Persistent else PreviewDataPersistence.Ephemeral
                    )
                }
            )
        }
        OutlinedButton(onClick = viewModel::clearPreviewData) { Text("清除预览数据") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                exportLauncher.launch(document.metadata.displayName + "-preview-data.zqd")
            }) { Text("导出加密备份") }
            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/vnd.zhique.runtime-data-backup", "application/octet-stream"))
            }) { Text("恢复加密备份") }
        }
        backupStatus?.let { message -> NoticeBanner(message) }
        NoticeBanner("备份只包含 weaver.data 和 weaver.storage 的公开项目数据；私密运行时配置、浏览器缓存和生成 APK 的数据沙箱不会进入预览备份。")
    }
    backupAction?.let { action ->
        AlertDialog(
            onDismissRequest = { backupAction = null; backupPassword = "" },
            title = { Text(if (action == PreviewBackupAction.Export) "导出预览数据备份" else "恢复预览数据备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份密码至少 12 位。恢复会替换当前项目的预览数据与项目文件。")
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
                    val uri = backupUri ?: return@TextButton
                    scope.launch {
                        val result = runCatching {
                            require(backupPassword.length >= 12) { "备份密码至少需要 12 位。" }
                            if (action == PreviewBackupAction.Export) {
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    output.write(dataManager.exportEncryptedBackup(document.metadata.id, backupPassword))
                                } ?: throw IllegalArgumentException("无法写入备份文件。")
                                "预览数据备份已导出。"
                            } else {
                                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    ?: throw IllegalArgumentException("无法读取备份文件。")
                                viewModel.stopPreview()
                                dataManager.restoreEncryptedBackup(document.metadata.id, backupPassword, bytes)
                                viewModel.runPreview()
                                "预览数据备份已恢复。"
                            }
                        }
                        backupStatus = result.getOrElse { "数据备份未完成：" + (it.message ?: "未知错误") }
                        backupAction = null
                        backupPassword = ""
                    }
                }) { Text(if (action == PreviewBackupAction.Export) "确认导出" else "确认恢复") }
            },
            dismissButton = { TextButton(onClick = { backupAction = null; backupPassword = "" }) { Text("取消") } }
        )
    }
}

private enum class PreviewBackupAction { Export, Import }

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

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        require(output.size() + read <= maxBytes) { "导入文件不能超过 ${maxBytes / (1024 * 1024)} MB。" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
