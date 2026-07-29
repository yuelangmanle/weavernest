package com.zhique.studio

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhique.core.project.ApiConnectionInput
import com.zhique.core.project.ApiConnectionPolicy
import com.zhique.core.project.ApiConnectionValidation
import com.zhique.core.project.BuildPlanner
import com.zhique.core.project.BuildRecord
import com.zhique.core.project.CodeImportAnalyzer
import com.zhique.core.project.ExternalCodeImport
import com.zhique.core.project.ExternalCodeDraft
import com.zhique.core.project.ImportAnalysis
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectDuplicationPolicy
import com.zhique.core.project.ProjectMetadata
import com.zhique.core.project.PreviewDataPersistence
import com.zhique.core.project.ProjectReleasePolicy
import com.zhique.core.project.PromptLanguage
import com.zhique.core.project.ReleaseUpdatePolicy
import com.zhique.core.project.RuntimeLogRedactor
import com.zhique.core.project.UpdateAvailability
import com.zhique.core.stabilization.ProjectArea
import com.zhique.core.template.TemplatePublication
import com.zhique.core.template.TemplatePublicationPolicy
import com.zhique.core.template.TemplateStatus
import com.zhique.core.template.DeviceCapabilityDiagnosticTemplate
import com.zhique.core.template.BuiltInCapabilityTemplates
import com.zhique.runtime.PreviewDataManager
import com.zhique.studio.data.AiSettings
import com.zhique.studio.data.AiSettingsStore
import com.zhique.studio.data.GitHubReleaseClient
import com.zhique.studio.data.GitHubReleaseInfo
import com.zhique.studio.data.OpenAiCompatibleClient
import com.zhique.studio.data.ProjectRepository
import com.zhique.studio.data.ProjectZipExport
import com.zhique.studio.data.ProjectZipImport
import com.zhique.studio.data.RecycledProject
import com.zhique.studio.data.ProjectSnapshot
import com.zhique.studio.data.ZipImportReview
import com.zhique.studio.build.ApkAssembler
import com.zhique.studio.build.GeneratedApkFiles
import com.zhique.studio.build.ProjectKeyStore
import com.zhique.studio.integrations.ApilotProfile
import java.util.Locale
import java.util.UUID
import java.util.Base64
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StudioLanguage { Chinese, English }

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val release: GitHubReleaseInfo) : UpdateUiState
    data class DownloadAvailable(val release: GitHubReleaseInfo) : UpdateUiState
    data class PackageMissing(val release: GitHubReleaseInfo) : UpdateUiState
    data class DownloadQueued(val release: GitHubReleaseInfo) : UpdateUiState
    data class DownloadFinished(val release: GitHubReleaseInfo) : UpdateUiState
    data class DownloadFailed(val message: String) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

enum class PreviewRuntimeStatus { Idle, Running, Stopped, Error }

sealed interface BuildUiState {
    data object Idle : BuildUiState
    data class Validated(val candidateVersionCode: Int) : BuildUiState
    data object Building : BuildUiState
    data class Succeeded(val artifactName: String) : BuildUiState
    data class Failed(val message: String) : BuildUiState
}

data class PreviewRuntimeUiState(
    val status: PreviewRuntimeStatus = PreviewRuntimeStatus.Idle,
    val runToken: Long = 0L,
    val logs: List<String> = emptyList()
)

data class StudioUiState(
    val documents: List<ProjectDocument> = emptyList(),
    val recycledProjects: List<RecycledProject> = emptyList(),
    val projectSnapshots: List<ProjectSnapshot> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedTab: ProjectArea = ProjectArea.Create,
    val language: StudioLanguage = StudioLanguage.Chinese,
    val importAnalysis: ImportAnalysis? = null,
    val pendingProjectImport: ExternalCodeDraft? = null,
    val pendingZipImport: ZipImportReview? = null,
    val aiDraft: String? = null,
    val isAiRequestRunning: Boolean = false,
    val update: UpdateUiState = UpdateUiState.Idle,
    val build: BuildUiState = BuildUiState.Idle,
    val previewRuntime: PreviewRuntimeUiState = PreviewRuntimeUiState(),
    val notice: String? = null
) {
    val selectedDocument: ProjectDocument?
        get() = documents.firstOrNull { it.metadata.id == selectedProjectId }
}

data class TemplateDefinition(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val capabilities: Set<String>,
    val html: String,
    val status: TemplateStatus = TemplateStatus.Hidden,
    val minimumApi: Int = 29,
    val verificationScenario: String = ""
)

object TemplateCatalog {
    private fun convert(template: com.zhique.core.template.BuiltInProjectTemplate, status: TemplateStatus) = TemplateDefinition(
        id = template.id,
        title = template.title,
        category = template.category,
        description = template.description,
        capabilities = template.capabilities,
        html = template.html,
        status = status,
        minimumApi = template.minimumApi,
        verificationScenario = template.verificationScenario
    )

    val all: List<TemplateDefinition> = buildList {
        add(convert(DeviceCapabilityDiagnosticTemplate.definition, TemplateStatus.Experimental))
        addAll(BuiltInCapabilityTemplates.all.map { template ->
            convert(template, if (template.id == "forms") TemplateStatus.Available else TemplateStatus.Experimental)
        })
    }

    val visible: List<TemplateDefinition>
        get() = TemplatePublicationPolicy.visible(all.map { TemplatePublication(it.id, it.status) })
            .mapNotNull { publication -> all.firstOrNull { it.id == publication.id } }

    val blankProjectHtml: String = """
        <!doctype html><html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>空白工具</title></head>
        <body><main><h1>空白工具</h1><p>在文件区粘贴或编写 HTML、CSS 和 JavaScript，然后点击运行。</p></main></body></html>
    """.trimIndent()
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val projectRepository = ProjectRepository(application)
    private val aiSettingsStore = AiSettingsStore(application)
    private val aiClient = OpenAiCompatibleClient()
    private val releaseClient = GitHubReleaseClient()
    private val apkAssembler = ApkAssembler(application)
    private val projectKeyStore = ProjectKeyStore(application)
    private val fileSaveJobs = mutableMapOf<String, Job>()
    private val dirtyProjectIds = linkedSetOf<String>()
    private var queuedDownloadId: Long? = null
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == queuedDownloadId) refreshDownloadStatus(downloadId)
        }
    }

    private val initialState = StudioUiState(
        documents = projectRepository.load(),
        recycledProjects = projectRepository.loadRecycleBin()
    )
    private val stateStream = MutableStateFlow(initialState)
    val uiState: StateFlow<StudioUiState> = stateStream.asStateFlow()

    private var composeState: StudioUiState by mutableStateOf(initialState)
    var state: StudioUiState
        get() = composeState
        private set(value) {
            composeState = value
            stateStream.value = value
        }

    init {
        val applicationContext = getApplication<Application>()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            applicationContext.registerReceiver(downloadReceiver, filter)
        }
    }

    fun createBlankProject(displayName: String) {
        createProject(displayName, emptySet(), TemplateCatalog.blankProjectHtml)
    }

    fun createFromTemplate(template: TemplateDefinition) {
        createProject(template.title, template.capabilities, template.html)
    }

    fun selectProject(projectId: String) {
        state = state.copy(
            selectedProjectId = projectId,
            selectedTab = ProjectArea.Create,
            projectSnapshots = runCatching { projectRepository.loadSnapshots(projectId) }.getOrDefault(emptyList()),
            build = BuildUiState.Idle,
            notice = null
        )
    }

    fun duplicateProject(projectId: String) {
        flushPendingProject(projectId)
        val source = state.documents.firstOrNull { it.metadata.id == projectId } ?: return
        runCatching {
            ProjectDuplicationPolicy.duplicate(source, UUID.randomUUID().toString())
        }.onSuccess { copied ->
            projectRepository.save(copied)
            state = state.copy(
                documents = listOf(copied) + state.documents,
                selectedProjectId = copied.metadata.id,
                selectedTab = ProjectArea.Create,
                notice = "已创建独立副本；它使用新的包名和签名身份。"
            )
        }.onFailure { error -> state = state.copy(notice = error.message ?: "无法复制项目。") }
    }

    fun renameProject(projectId: String, displayName: String) {
        val normalized = displayName.trim()
        if (normalized.isBlank()) {
            state = state.copy(notice = "项目名称不能为空。")
            return
        }
        val document = state.documents.firstOrNull { it.metadata.id == projectId } ?: return
        replaceDocument(document.copy(metadata = document.metadata.copy(displayName = normalized.take(80))))
        state = state.copy(notice = "项目已重命名为“${normalized.take(80)}”。")
    }

    fun moveProjectToRecycleBin(projectId: String) {
        flushPendingProject(projectId)
        runCatching { projectRepository.moveToRecycleBin(projectId) }
            .onSuccess { recycled ->
                state = state.copy(
                    documents = state.documents.filterNot { it.metadata.id == projectId },
                    recycledProjects = listOf(recycled) + state.recycledProjects,
                    selectedProjectId = state.selectedProjectId.takeUnless { it == projectId },
                    notice = "项目已移入回收站，可在 织雀 内恢复。"
                )
            }
            .onFailure { error -> state = state.copy(notice = error.message ?: "无法移入回收站。") }
    }

    fun restoreProjectFromRecycleBin(recycleId: String) {
        runCatching { projectRepository.restoreFromRecycleBin(recycleId) }
            .onSuccess { restored ->
                state = state.copy(
                    documents = listOf(restored) + state.documents,
                    recycledProjects = projectRepository.loadRecycleBin(),
                    notice = "已恢复项目“${restored.metadata.displayName}”。"
                )
            }
            .onFailure { error -> state = state.copy(notice = error.message ?: "无法恢复项目。") }
    }

    fun refreshProjectSnapshots(projectId: String) {
        state = state.copy(projectSnapshots = runCatching { projectRepository.loadSnapshots(projectId) }.getOrDefault(emptyList()))
    }

    fun restoreProjectSnapshot(projectId: String, snapshotId: String) {
        runCatching { projectRepository.restoreSnapshot(projectId, snapshotId) }
            .onSuccess { restored ->
                state = state.copy(
                    documents = state.documents.map { document -> if (document.metadata.id == projectId) restored else document },
                    projectSnapshots = projectRepository.loadSnapshots(projectId),
                    notice = "已恢复项目快照；恢复前的当前版本也已保留为新快照。"
                )
            }
            .onFailure { error -> state = state.copy(notice = error.message ?: "无法恢复项目快照。") }
    }

    fun exportProject(projectId: String) {
        flushPendingProject(projectId)
        val document = state.documents.firstOrNull { it.metadata.id == projectId } ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ProjectZipExport.write(
                        File(getApplication<Application>().cacheDir, "project-exports"),
                        document
                    )
                }
            }
            result.onSuccess { archive ->
                val application = getApplication<Application>()
                val uri = FileProvider.getUriForFile(application, "${application.packageName}.projectexport", archive)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .also { intent -> intent.clipData = ClipData.newRawUri("织雀项目导出", uri) }
                application.startActivity(Intent.createChooser(send, "导出项目 ZIP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                state = state.copy(notice = "已生成项目 ZIP；不含 API Key、预览数据或签名密钥。")
            }.onFailure { error -> state = state.copy(notice = error.message ?: "无法导出项目 ZIP。") }
        }
    }

    fun closeProject() {
        state.selectedProjectId?.let(::flushPendingProject)
        state = state.copy(selectedProjectId = null, projectSnapshots = emptyList(), importAnalysis = null, pendingProjectImport = null, aiDraft = null, build = BuildUiState.Idle, notice = null)
    }

    fun selectTab(tab: ProjectArea) {
        state = state.copy(selectedTab = tab, notice = null)
    }

    fun toggleLanguage() {
        state = state.copy(
            language = if (state.language == StudioLanguage.Chinese) StudioLanguage.English else StudioLanguage.Chinese
        )
    }

    fun showNotice(message: String) {
        state = state.copy(notice = message)
    }

    fun updateFile(path: String, content: String) {
        val document = state.selectedDocument ?: return
        val updated = document.withFile(path, content)
        state = state.copy(
            documents = state.documents.map { current -> if (current.metadata.id == updated.metadata.id) updated else current },
            importAnalysis = CodeImportAnalyzer.analyze(updated.files)
        )
        scheduleFileSave(updated.metadata.id)
    }

    fun createFromPastedCode(projectName: String, source: String) {
        if (source.isBlank()) {
            state = state.copy(notice = "请先粘贴 HTML、CSS 或 JavaScript 代码。")
            return
        }
        val draft = ExternalCodeImport.prepare(projectName, source)
        val indexHtml = draft.files.getValue("index.html")
        createProject(
            displayName = draft.projectName,
            capabilities = draft.analysis.suggestedCapabilities,
            indexHtml = indexHtml,
            extraFiles = draft.files - "index.html"
        )
        state = state.copy(selectedTab = ProjectArea.Create, importAnalysis = draft.analysis, notice = "代码已创建为项目；可编辑后点击运行。")
    }

    fun pasteIntoSelectedProject(source: String): Boolean {
        val document = state.selectedDocument ?: return false
        if (source.isBlank()) {
            state = state.copy(notice = "没有可导入的代码。")
            return false
        }
        val draft = ExternalCodeImport.prepare(document.metadata.displayName, source)
        state = state.copy(
            pendingProjectImport = draft,
            importAnalysis = draft.analysis,
            notice = "请先查看文件差异和预览，再确认写入当前项目。"
        )
        return true
    }

    fun commitPendingProjectImport(): Boolean {
        val draft = state.pendingProjectImport ?: return false
        updateSelected { current -> current.copy(files = current.files + draft.files) }
        state = state.copy(pendingProjectImport = null, selectedTab = ProjectArea.Create, notice = "外部代码已写入项目；请运行预览确认。")
        return true
    }

    fun discardPendingProjectImport() {
        state = state.copy(pendingProjectImport = null)
    }

    fun selectCapabilities(capabilities: Set<String>) {
        updateSelected { document -> document.copy(metadata = document.metadata.copy(capabilities = capabilities)) }
    }

    fun updatePreviewDataPersistence(persistence: PreviewDataPersistence) {
        updateSelected { document ->
            document.copy(metadata = document.metadata.copy(previewDataPersistence = persistence))
        }
        state = state.copy(
            notice = if (persistence == PreviewDataPersistence.Persistent) {
                "此项目的预览数据会在重新打开织雀后保留。"
            } else {
                "此项目下次运行预览前会清除普通预览数据；私密运行时配置不受影响。"
            }
        )
    }

    fun updateBuildMetadata(displayName: String, versionName: String, packageName: String) {
        runCatching {
            updateSelected { document ->
                val metadata = document.metadata.copy(displayName = displayName.trim(), versionName = versionName.trim())
                val withPackage = if (metadata.packageName == packageName.trim()) metadata else ProjectReleasePolicy.changePackageName(metadata, packageName.trim())
                document.copy(metadata = withPackage)
            }
        }.onFailure { error -> state = state.copy(notice = error.message ?: "无法更新构建信息。") }
    }

    fun updateProjectIcon(bytes: ByteArray) {
        if (bytes.size !in 8..2_000_000 || !bytes.isPng()) {
            state = state.copy(notice = "应用图标必须是小于 2MB 的 PNG 图片。")
            return
        }
        updateSelected { document ->
            document.copy(
                metadata = document.metadata.copy(iconAssetPath = PROJECT_ICON_PATH),
                binaryAssets = document.binaryAssets + (PROJECT_ICON_PATH to Base64.getEncoder().withoutPadding().encodeToString(bytes))
            )
        }
        state = state.copy(notice = "应用图标已保存到当前项目。")
    }

    fun clearProjectIcon() {
        updateSelected { document ->
            document.copy(
                metadata = document.metadata.copy(iconAssetPath = null),
                binaryAssets = document.binaryAssets - PROJECT_ICON_PATH
            )
        }
        state = state.copy(notice = "已恢复使用织雀默认项目图标。")
    }

    fun prepareBuild() {
        val document = state.selectedDocument ?: return
        runCatching {
            BuildPlanner.prepare(document)
        }.onSuccess { plan ->
            state = state.copy(
                build = BuildUiState.Validated(plan.candidateProject.versionCode),
                notice = "构建检查通过：将生成 ${plan.candidateProject.versionName} (${plan.candidateProject.versionCode})；尚未锁定包名或版本。"
            )
        }.onFailure { error ->
            state = state.copy(build = BuildUiState.Failed(error.message ?: "无法准备构建。"), notice = error.message ?: "无法准备构建。")
        }
    }

    fun buildApk(backupPassword: String) {
        val document = state.selectedDocument ?: return
        state = state.copy(build = BuildUiState.Building, notice = "正在组装并签名 APK…")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val plan = BuildPlanner.prepare(document)
                    val assembled = apkAssembler.assemble(document, plan, backupPassword.ifBlank { null })
                    plan.copy(candidateProject = assembled.candidateMetadata) to assembled
                }
            }
            result.onSuccess { (plan, assembled) ->
                val current = state.documents.firstOrNull { it.metadata.id == document.metadata.id }
                if (current == null) {
                    state = state.copy(build = BuildUiState.Failed("APK 已生成，但项目已关闭；未写入构建记录。"), notice = "APK 已生成：${assembled.artifact.name}")
                    return@onSuccess
                }
                runCatching {
                    val committed = BuildPlanner.commitSuccessfulAssembly(current, plan)
                    committed.copy(
                        buildHistory = committed.buildHistory + BuildRecord(
                            versionName = committed.metadata.versionName,
                            versionCode = committed.metadata.versionCode,
                            createdAtEpochMillis = System.currentTimeMillis(),
                            status = "succeeded",
                            message = "APK 已通过 v2/v3 签名验证。",
                            artifactFileName = assembled.artifact.name,
                            artifactSha256 = assembled.artifactSha256,
                            signingKeyId = committed.metadata.signingKeyId
                        )
                    )
                }.onSuccess { committed ->
                    replaceDocument(committed)
                    state = state.copy(build = BuildUiState.Succeeded(assembled.artifact.name), notice = "APK 已生成：${assembled.artifact.name}")
                }.onFailure { error ->
                    state = state.copy(build = BuildUiState.Failed(error.message ?: "APK 已生成，但项目已在构建期间修改。"), notice = error.message ?: "APK 已生成，但项目已在构建期间修改。")
                }
            }.onFailure { error ->
                val message = error.message ?: "APK 构建失败。"
                state = state.copy(build = BuildUiState.Failed(message), notice = message)
            }
        }
    }

    fun installLatestBuiltApk() {
        val artifact = latestBuiltArtifact() ?: return
        val application = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !application.packageManager.canRequestPackageInstalls()) {
            application.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${application.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            state = state.copy(notice = "请在系统设置中允许织雀安装未知来源应用，然后再次点击安装。")
            return
        }
        val uri = FileProvider.getUriForFile(application, "${application.packageName}.generatedapk", artifact)
        application.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun shareLatestBuiltApk() {
        val artifact = latestBuiltArtifact() ?: return
        val application = getApplication<Application>()
        val uri = FileProvider.getUriForFile(application, "${application.packageName}.generatedapk", artifact)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { intent -> intent.clipData = ClipData.newRawUri("Zhique APK", uri) }
        application.startActivity(Intent.createChooser(send, "发送 APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun exportProjectSigningBackup(backupPassword: String) {
        val document = state.selectedDocument ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = projectKeyStore.exportBackup(document.metadata.id, backupPassword)
                    File(getApplication<Application>().cacheDir, "signing-backups").apply { mkdirs() }
                        .let { directory -> File(directory, "${document.metadata.id}.zqkey").apply { writeBytes(bytes) } }
                }
            }
            result.onSuccess { backup ->
                val application = getApplication<Application>()
                val uri = FileProvider.getUriForFile(application, "${application.packageName}.projectbackup", backup)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/vnd.zhique.signing-key-backup")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .also { intent -> intent.clipData = ClipData.newRawUri("织雀项目签名备份", uri) }
                application.startActivity(Intent.createChooser(send, "导出加密签名备份").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                state = state.copy(notice = "已生成加密签名备份；请妥善保存备份文件和密码。")
            }.onFailure { error -> state = state.copy(notice = error.message ?: "无法导出签名备份。") }
        }
    }

    fun restoreProjectSigningBackup(encryptedBackup: ByteArray, backupPassword: String) {
        val document = state.selectedDocument ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val provision = projectKeyStore.restoreBackup(document.metadata, encryptedBackup, backupPassword)
                    val backupDirectory = File(getApplication<Application>().filesDir, "project-key-backups/${document.metadata.id}").apply { mkdirs() }
                    val backupName = provision.metadata.signingBackupId ?: "backup-${provision.signingKey.keyId}.zqkey"
                    File(backupDirectory, backupName).writeBytes(encryptedBackup)
                    provision.metadata
                }
            }
            result.onSuccess { metadata ->
                val current = state.documents.firstOrNull { it.metadata.id == document.metadata.id } ?: return@onSuccess
                replaceDocument(current.copy(metadata = metadata))
                state = state.copy(notice = "已恢复项目签名身份；后续构建可继续更新同一应用。")
            }.onFailure { error -> state = state.copy(notice = error.message ?: "无法恢复签名备份。") }
        }
    }

    fun importPlainText(fileName: String, content: String) {
        createFromPastedCode(fileName.substringBeforeLast('.').ifBlank { "导入项目" }, content)
    }

    fun inspectZip(fileName: String, bytes: ByteArray) {
        runCatching {
            ProjectZipImport.inspect(getApplication<Application>().cacheDir, fileName, bytes)
        }.onSuccess { review ->
            state = state.copy(
                pendingZipImport = review,
                importAnalysis = review.analysis,
                notice = "已读取 ZIP，请确认文件和能力后再创建项目。"
            )
        }.onFailure { error ->
            state = state.copy(pendingZipImport = null, notice = error.message ?: "ZIP 导入失败。")
        }
    }

    fun commitZipImport(): Boolean {
        val review = state.pendingZipImport ?: run {
            state = state.copy(notice = "没有等待确认的 ZIP 导入。")
            return false
        }
        val index = review.textFiles["index.html"] ?: run {
            state = state.copy(notice = "ZIP 缺少 HTML 入口文件。")
            return false
        }
        createProject(
            displayName = review.projectName,
            capabilities = review.analysis.suggestedCapabilities,
            indexHtml = index,
            extraFiles = review.textFiles - "index.html",
            binaryAssets = review.binaryAssets
        )
        state = state.copy(
            pendingZipImport = null,
            selectedTab = ProjectArea.Create,
            importAnalysis = review.analysis,
            notice = "ZIP 已创建为项目；请在编辑器和预览中确认。"
        )
        return true
    }

    fun discardZipImport() {
        if (state.pendingZipImport != null) state = state.copy(pendingZipImport = null)
    }

    fun analyzeSelectedProject() {
        val document = state.selectedDocument ?: return
        state = state.copy(importAnalysis = CodeImportAnalyzer.analyze(document.files))
    }

    fun copyExternalPrompt(language: PromptLanguage = state.promptLanguage()): String = state.selectedDocument
        ?.let { document -> com.zhique.core.project.PromptPack.default(language).renderForExternalModel(document.metadata.displayName) }
        .orEmpty()

    fun loadAiSettings(): AiSettings = aiSettingsStore.load()

    fun saveAiSettings(settings: AiSettings): Boolean {
        if (!validateAiSettings(settings)) return false
        aiSettingsStore.save(settings)
        state = state.copy(notice = "AI 接口设置已保存到本机安全存储。")
        return true
    }

    fun requestAi(prompt: String) {
        val document = state.selectedDocument ?: return
        if (prompt.isBlank()) return
        val settings = aiSettingsStore.load()
        if (!validateAiSettings(settings)) return
        state = state.copy(isAiRequestRunning = true, notice = null)
        viewModelScope.launch {
            runCatching { aiClient.generate(settings, document.metadata.displayName, prompt.trim(), state.promptLanguage()) }
                .onSuccess { response -> state = state.copy(aiDraft = response, isAiRequestRunning = false) }
                .onFailure { error -> state = state.copy(isAiRequestRunning = false, notice = error.message ?: "AI 请求失败。") }
        }
    }

    fun requestAiRuntimeRepair() {
        val logs = state.previewRuntime.logs.takeLast(12)
        if (logs.isEmpty()) {
            state = state.copy(notice = "当前没有可发送给 AI 的运行日志。")
            return
        }
        val report = logs.joinToString("\n") { RuntimeLogRedactor.redact(it) }
        requestAi(
            "请修复当前项目的运行错误。先分析以下已经脱敏的运行日志，再输出可导入的完整 index.html 草案；不要添加未在织雀 Runtime API 中声明的方法。\n\n运行日志：\n$report"
        )
    }

    fun applyAiDraftToIndex() {
        val draft = state.aiDraft ?: return
        updateSelected { document -> document.withFile("index.html", draft) }
        state = state.copy(aiDraft = null, selectedTab = ProjectArea.Run, notice = "AI 草案已写入 index.html；请在预览中确认。")
        analyzeSelectedProject()
    }

    fun runPreview() {
        state = state.copy(
            selectedTab = ProjectArea.Run,
            previewRuntime = state.previewRuntime.copy(
                status = PreviewRuntimeStatus.Running,
                runToken = state.previewRuntime.runToken + 1,
                logs = listOf("正在加载预览…")
            ),
            notice = null
        )
    }

    fun stopPreview() {
        state = state.copy(
            previewRuntime = state.previewRuntime.copy(
                status = PreviewRuntimeStatus.Stopped,
                logs = state.previewRuntime.logs + "预览已停止。"
            )
        )
    }

    fun onPreviewReady() {
        if (state.previewRuntime.status == PreviewRuntimeStatus.Running) {
            state = state.copy(previewRuntime = state.previewRuntime.copy(logs = state.previewRuntime.logs + "预览正在运行。"))
        }
    }

    fun onPreviewLog(message: String, isError: Boolean = false) {
        val cleanMessage = RuntimeLogRedactor.redact(message).trim().take(500)
        if (cleanMessage.isBlank()) return
        val logs = (state.previewRuntime.logs + cleanMessage).takeLast(40)
        state = state.copy(
            previewRuntime = state.previewRuntime.copy(
                status = if (isError) PreviewRuntimeStatus.Error else state.previewRuntime.status,
                logs = logs
            )
        )
    }

    fun dismissAiDraft() {
        state = state.copy(aiDraft = null)
    }

    fun clearPreviewData() {
        val projectId = state.selectedDocument?.metadata?.id ?: return
        PreviewDataManager(getApplication()).clearProjectData(projectId)
        state = state.copy(notice = "预览数据已清除。")
    }

    fun checkForUpdate() {
        state = state.copy(update = UpdateUiState.Checking, notice = null)
        viewModelScope.launch {
            runCatching { releaseClient.latest(BuildConfig.GITHUB_REPOSITORY) }
                .onSuccess { release ->
                    val availability = ReleaseUpdatePolicy.availability(BuildConfig.VERSION_NAME, release.tagName, release.apkName)
                    val update = when (availability) {
                        UpdateAvailability.UpToDate -> UpdateUiState.UpToDate(release)
                        UpdateAvailability.DownloadAvailable -> UpdateUiState.DownloadAvailable(release)
                        UpdateAvailability.PackageMissing -> UpdateUiState.PackageMissing(release)
                    }
                    state = state.copy(
                        update = update,
                        notice = when (availability) {
                            UpdateAvailability.DownloadAvailable -> "发现新版本 ${release.tagName}，可直接下载。"
                            UpdateAvailability.PackageMissing -> "发现新版本 ${release.tagName}，但发布方尚未上传 APK。"
                            UpdateAvailability.UpToDate -> "当前已是最新版本。"
                        }
                    )
                }
                .onFailure { error ->
                    val message = error.message ?: "无法检查更新。"
                    state = state.copy(update = UpdateUiState.Failed(message), notice = message)
                }
        }
    }

    fun downloadLatestApk() {
        val release = (state.update as? UpdateUiState.DownloadAvailable)?.release ?: return
        val url = release.apkUrl ?: return
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(release.apkName ?: "织雀-${release.tagName}.apk")
            .setDescription("来自 GitHub Releases 的织雀安装包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, release.apkName ?: "织雀-${release.tagName}.apk")
        val manager = getApplication<Application>().getSystemService(DownloadManager::class.java)
        queuedDownloadId = manager.enqueue(request)
        state = state.copy(update = UpdateUiState.DownloadQueued(release), notice = "已开始下载更新；完成后请在系统下载通知中安装。")
    }

    fun applyApilotProfile(profile: ApilotProfile) {
        val settings = AiSettings(
            endpoint = profile.endpoint,
            model = profile.model,
            apiKey = profile.apiKey.orEmpty(),
            providerId = profile.providerId,
            protocolId = profile.protocolId
        )
        if (!validateAiSettings(settings)) return
        aiSettingsStore.save(settings)
        state = state.copy(notice = "已从 Apilot 导入 ${profile.providerId} 方案。")
    }

    private fun validateAiSettings(settings: AiSettings): Boolean = when (
        val validation = ApiConnectionPolicy.validate(
            ApiConnectionInput(
                endpoint = settings.endpoint,
                model = settings.model,
                providerId = settings.providerId,
                protocolId = settings.protocolId,
                apiKeyLength = settings.apiKey.length
            )
        )
    ) {
        ApiConnectionValidation.Valid -> true
        is ApiConnectionValidation.Invalid -> {
            state = state.copy(notice = validation.message)
            false
        }
    }

    private fun createProject(
        displayName: String,
        capabilities: Set<String>,
        indexHtml: String,
        extraFiles: Map<String, String> = emptyMap(),
        binaryAssets: Map<String, String> = emptyMap()
    ) {
        val packageSlug = displayName.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "")
            .ifBlank { "tool" }
            .take(18)
        val project = ProjectMetadata.create(displayName.trim().ifBlank { "未命名工具" }, "app.zhique.${packageSlug}${UUID.randomUUID().toString().take(6)}")
        val document = ProjectDocument(
            metadata = project.copy(capabilities = capabilities),
            files = mapOf("index.html" to indexHtml) + extraFiles,
            binaryAssets = binaryAssets
        )
        projectRepository.save(document)
        state = state.copy(
            documents = listOf(document) + state.documents,
            selectedProjectId = document.metadata.id,
            selectedTab = ProjectArea.Create,
            importAnalysis = CodeImportAnalyzer.analyze(document.files),
            notice = "项目已创建。"
        )
    }

    private fun updateSelected(transform: (ProjectDocument) -> ProjectDocument) {
        val document = state.selectedDocument ?: return
        replaceDocument(transform(document))
    }

    private fun replaceDocument(document: ProjectDocument) {
        val updated = document.copy(metadata = document.metadata.copy(lastModifiedEpochMillis = System.currentTimeMillis()))
        projectRepository.save(updated)
        state = state.copy(documents = state.documents.map { current -> if (current.metadata.id == updated.metadata.id) updated else current })
    }

    private fun refreshDownloadStatus(downloadId: Long) {
        val release = (state.update as? UpdateUiState.DownloadQueued)?.release ?: return
        val manager = getApplication<Application>().getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            state = when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> state.copy(
                    update = UpdateUiState.DownloadFinished(release),
                    notice = "更新下载完成，请在系统下载通知中安装。"
                )
                DownloadManager.STATUS_FAILED -> state.copy(
                    update = UpdateUiState.DownloadFailed("更新下载失败，请检查网络后重试。"),
                    notice = "更新下载失败，请检查网络后重试。"
                )
                else -> state
            }
        }
    }

    private fun latestBuiltArtifact(): java.io.File? {
        val record = state.selectedDocument?.buildHistory?.lastOrNull { it.status == "succeeded" && it.artifactFileName != null }
            ?: run {
                state = state.copy(notice = "当前项目没有可安装或发送的已验证 APK。")
                return null
            }
        return runCatching { GeneratedApkFiles.output(getApplication(), requireNotNull(record.artifactFileName)) }
            .getOrNull()
            ?.takeIf { it.isFile }
            ?: run {
                state = state.copy(notice = "已验证 APK 文件不在本机，请重新构建。")
                null
            }
    }

    override fun onCleared() {
        dirtyProjectIds.toList().forEach(::flushPendingProject)
        getApplication<Application>().unregisterReceiver(downloadReceiver)
        projectRepository.close()
        super.onCleared()
    }

    private fun scheduleFileSave(projectId: String) {
        dirtyProjectIds += projectId
        fileSaveJobs.remove(projectId)?.cancel()
        fileSaveJobs[projectId] = viewModelScope.launch {
            delay(500)
            flushPendingProject(projectId, cancelScheduledSave = false)
        }
    }

    private fun flushPendingProject(projectId: String, cancelScheduledSave: Boolean = true) {
        val scheduledSave = fileSaveJobs.remove(projectId)
        if (cancelScheduledSave) scheduledSave?.cancel()
        if (projectId !in dirtyProjectIds) return
        state.documents.firstOrNull { document -> document.metadata.id == projectId }?.let(projectRepository::save)
        dirtyProjectIds -= projectId
    }

    private fun ByteArray.isPng(): Boolean = size >= PNG_HEADER.size && PNG_HEADER.indices.all { index -> this[index] == PNG_HEADER[index] }

    private companion object {
        const val PROJECT_ICON_PATH = "assets/zhique-app-icon.png"
        val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }

}

private fun StudioUiState.promptLanguage(): PromptLanguage = when (language) {
    StudioLanguage.Chinese -> PromptLanguage.ZhCn
    StudioLanguage.English -> PromptLanguage.En
}
