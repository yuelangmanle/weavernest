package com.zhique.studio

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhique.core.project.BuildPlanner
import com.zhique.core.project.BuildRecord
import com.zhique.core.project.CodeImportAnalyzer
import com.zhique.core.project.ExternalCodeImport
import com.zhique.core.project.ImportAnalysis
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import com.zhique.core.project.ProjectReleasePolicy
import com.zhique.core.project.PromptLanguage
import com.zhique.core.project.ReleaseUpdatePolicy
import com.zhique.core.project.UpdateAvailability
import com.zhique.core.template.TemplatePublication
import com.zhique.core.template.TemplatePublicationPolicy
import com.zhique.core.template.TemplateStatus
import com.zhique.studio.data.AiSettings
import com.zhique.studio.data.AiSettingsStore
import com.zhique.studio.data.GitHubReleaseClient
import com.zhique.studio.data.GitHubReleaseInfo
import com.zhique.studio.data.OpenAiCompatibleClient
import com.zhique.studio.data.ProjectStore
import com.zhique.studio.integrations.ApilotProfile
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile

enum class StudioLanguage { Chinese, English }

enum class WorkspaceTab(val chinese: String, val english: String) {
    Ai("AI", "AI"),
    Files("文件", "Files"),
    Preview("预览", "Preview"),
    Capabilities("能力", "Capabilities"),
    Build("构建", "Build"),
    Data("数据", "Data")
}

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

data class PreviewRuntimeUiState(
    val status: PreviewRuntimeStatus = PreviewRuntimeStatus.Idle,
    val runToken: Long = 0L,
    val logs: List<String> = emptyList()
)

data class StudioUiState(
    val documents: List<ProjectDocument> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedTab: WorkspaceTab = WorkspaceTab.Ai,
    val language: StudioLanguage = StudioLanguage.Chinese,
    val importAnalysis: ImportAnalysis? = null,
    val aiDraft: String? = null,
    val isAiRequestRunning: Boolean = false,
    val update: UpdateUiState = UpdateUiState.Idle,
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
    val status: TemplateStatus = TemplateStatus.Hidden
)

object TemplateCatalog {
    val all = listOf(
        TemplateDefinition("camera", "拍照识别", "影像与媒体", "原生相机模块验证完成后开放。", setOf("camera", "network"), starterPage("拍照识别", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("music", "音乐播放器", "影像与媒体", "原生媒体模块验证完成后开放。", setOf("media_audio", "files", "network"), starterPage("音乐播放器", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("album", "相册管理", "影像与媒体", "原生相册模块验证完成后开放。", setOf("media_images", "files"), starterPage("相册管理", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("files", "文件工具", "文件与数据", "原生文件模块验证完成后开放。", setOf("files"), starterPage("文件工具", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("forms", "离线表单", "文件与数据", "离线填写并保存到项目数据。", emptySet(), starterPage("离线表单", "数据将保存在本机。"), TemplateStatus.Available),
        TemplateDefinition("location", "定位签到", "定位与传感器", "原生定位模块验证完成后开放。", setOf("location"), starterPage("定位签到", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("ble", "蓝牙 BLE 控制", "蓝牙与近场", "原生蓝牙模块验证完成后开放。", setOf("bluetooth_le"), starterPage("蓝牙 BLE 控制", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("nfc", "NFC 标签工具", "蓝牙与近场", "原生 NFC 模块验证完成后开放。", setOf("nfc"), starterPage("NFC 标签工具", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("wifi", "Wi-Fi 网络诊断", "Wi-Fi 与网络", "原生 Wi-Fi 模块验证完成后开放。", setOf("wifi_scan", "network"), starterPage("Wi-Fi 网络诊断", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("hotspot", "局部热点", "Wi-Fi 与网络", "局部热点模块验证完成后开放。", setOf("local_hotspot"), starterPage("局部热点", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("api", "API 数据面板", "Wi-Fi 与网络", "公开 API 请求界面正在验证。", setOf("network"), starterPage("API 数据面板", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("notifications", "通知提醒", "系统能力", "原生通知模块验证完成后开放。", setOf("notifications"), starterPage("通知提醒", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("contacts", "联系人助手", "系统能力", "原生联系人模块验证完成后开放。", setOf("contacts"), starterPage("联系人助手", "此模板尚未通过真机能力验收。")),
        TemplateDefinition("automation", "AI 自动化", "AI 与自动化", "运行时配置模块验证完成后开放。", setOf("network"), starterPage("AI 自动化", "此模板尚未通过真机能力验收。"))
    )

    val visible: List<TemplateDefinition>
        get() = TemplatePublicationPolicy.visible(all.map { TemplatePublication(it.id, it.status) })
            .mapNotNull { publication -> all.firstOrNull { it.id == publication.id } }

    private fun starterPage(title: String, copy: String): String = """
        <!doctype html>
        <html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1" />
        <style>body{font-family:sans-serif;margin:24px;background:#f7f8fa;color:#172033}button{padding:12px 16px;border:0;border-radius:8px;background:#155eef;color:white;font-size:16px}</style>
        </head><body><h1>$title</h1><p>$copy</p><button onclick="saveSample()">保存测试数据</button><p id="status"></p>
        <script>function saveSample(){window.weaver.data.set('lastAction',new Date().toISOString());document.getElementById('status').textContent='已保存到预览数据';}</script></body></html>
    """.trimIndent()
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val projectStore = ProjectStore(application)
    private val aiSettingsStore = AiSettingsStore(application)
    private val aiClient = OpenAiCompatibleClient()
    private val releaseClient = GitHubReleaseClient()
    private var queuedDownloadId: Long? = null
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == queuedDownloadId) refreshDownloadStatus(downloadId)
        }
    }

    var state by mutableStateOf(
        StudioUiState(documents = projectStore.load())
    )
        private set

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
        createProject(displayName, emptySet(), TemplateCatalog.visible.first().html)
    }

    fun createFromTemplate(template: TemplateDefinition) {
        createProject(template.title, template.capabilities, template.html)
    }

    fun selectProject(projectId: String) {
        state = state.copy(selectedProjectId = projectId, selectedTab = WorkspaceTab.Ai, notice = null)
    }

    fun closeProject() {
        state = state.copy(selectedProjectId = null, importAnalysis = null, aiDraft = null, notice = null)
    }

    fun selectTab(tab: WorkspaceTab) {
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
        updateSelected { document -> document.withFile(path, content) }
        analyzeSelectedProject()
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
        state = state.copy(selectedTab = WorkspaceTab.Files, importAnalysis = draft.analysis, notice = "代码已创建为项目；可编辑后点击运行。")
    }

    fun pasteIntoSelectedProject(source: String) {
        val document = state.selectedDocument ?: return
        if (source.isBlank()) {
            state = state.copy(notice = "没有可导入的代码。")
            return
        }
        val draft = ExternalCodeImport.prepare(document.metadata.displayName, source)
        updateSelected { current -> current.copy(files = current.files + draft.files) }
        state = state.copy(importAnalysis = draft.analysis, selectedTab = WorkspaceTab.Files, notice = "外部代码已写入项目；请运行预览确认。")
    }

    fun selectCapabilities(capabilities: Set<String>) {
        updateSelected { document -> document.copy(metadata = document.metadata.copy(capabilities = capabilities)) }
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

    fun prepareBuild() {
        val document = state.selectedDocument ?: return
        runCatching {
            BuildPlanner.prepare(document.metadata, document.files.keys + document.binaryAssets.keys)
        }.onSuccess { plan ->
            val updated = document.copy(
                metadata = plan.project,
                buildHistory = document.buildHistory + BuildRecord(
                    versionName = plan.project.versionName,
                    versionCode = plan.project.versionCode,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    status = "prepared",
                    message = "已生成本地模板组装计划；权限 ${plan.manifestPermissions.size} 项。"
                )
            )
            replaceDocument(updated)
            state = state.copy(notice = "构建计划已准备：${plan.project.versionName} (${plan.project.versionCode})")
        }.onFailure { error ->
            state = state.copy(notice = error.message ?: "无法准备构建。")
        }
    }

    fun importPlainText(fileName: String, content: String) {
        createFromPastedCode(fileName.substringBeforeLast('.').ifBlank { "导入项目" }, content)
    }

    fun importZip(fileName: String, bytes: ByteArray) {
        runCatching {
            val temporary = File.createTempFile("zhique-import", ".zip", getApplication<Application>().cacheDir).apply { writeBytes(bytes) }
            ZipFile(temporary).use { archive ->
                val text = linkedMapOf<String, String>()
                val binary = linkedMapOf<String, String>()
                archive.fileHeaders.filterNot { it.isDirectory }.forEach { header ->
                    archive.getInputStream(header).use { input ->
                        val data = input.readBytes()
                        if (header.fileName.isTextFile()) text[header.fileName] = data.decodeToString()
                        else binary[header.fileName] = Base64.encodeToString(data, Base64.NO_WRAP)
                    }
                }
                temporary.delete()
                text to binary
            }
        }.onSuccess { (files, assets) ->
            val index = files["index.html"] ?: files.entries.firstOrNull { it.key.endsWith(".html", true) }?.value
                ?: TemplateCatalog.visible.first().html
            createProject(fileName.substringBeforeLast('.').ifBlank { "导入项目" }, emptySet(), index, files - "index.html", assets)
        }.onFailure { error ->
            state = state.copy(notice = error.message ?: "ZIP 导入失败。")
        }
    }

    fun analyzeSelectedProject() {
        val document = state.selectedDocument ?: return
        state = state.copy(importAnalysis = CodeImportAnalyzer.analyze(document.files))
    }

    fun copyExternalPrompt(language: PromptLanguage = state.promptLanguage()): String = state.selectedDocument
        ?.let { document -> com.zhique.core.project.PromptPack.default(language).renderForExternalModel(document.metadata.displayName) }
        .orEmpty()

    fun loadAiSettings(): AiSettings = aiSettingsStore.load()

    fun saveAiSettings(settings: AiSettings) {
        aiSettingsStore.save(settings)
        state = state.copy(notice = "AI 接口设置已保存到本机安全存储。")
    }

    fun requestAi(prompt: String) {
        val document = state.selectedDocument ?: return
        if (prompt.isBlank()) return
        state = state.copy(isAiRequestRunning = true, notice = null)
        viewModelScope.launch {
            runCatching { aiClient.generate(aiSettingsStore.load(), document.metadata.displayName, prompt.trim(), state.promptLanguage()) }
                .onSuccess { response -> state = state.copy(aiDraft = response, isAiRequestRunning = false) }
                .onFailure { error -> state = state.copy(isAiRequestRunning = false, notice = error.message ?: "AI 请求失败。") }
        }
    }

    fun applyAiDraftToIndex() {
        val draft = state.aiDraft ?: return
        updateSelected { document -> document.withFile("index.html", draft) }
        state = state.copy(aiDraft = null, selectedTab = WorkspaceTab.Preview, notice = "AI 草案已写入 index.html；请在预览中确认。")
        analyzeSelectedProject()
    }

    fun runPreview() {
        state = state.copy(
            selectedTab = WorkspaceTab.Preview,
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
        val cleanMessage = message.trim().take(500)
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
        getApplication<Application>().getSharedPreferences("preview_$projectId", Context.MODE_PRIVATE).edit().clear().apply()
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
        aiSettingsStore.save(
            AiSettings(
                endpoint = profile.endpoint,
                model = profile.model,
                apiKey = profile.apiKey.orEmpty(),
                providerId = profile.providerId,
                protocolId = profile.protocolId
            )
        )
        state = state.copy(notice = "已从 Apilot 导入 ${profile.providerId} 方案。")
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
        projectStore.save(document)
        state = state.copy(
            documents = listOf(document) + state.documents,
            selectedProjectId = document.metadata.id,
            selectedTab = WorkspaceTab.Ai,
            importAnalysis = CodeImportAnalyzer.analyze(document.files),
            notice = "项目已创建。"
        )
    }

    private fun updateSelected(transform: (ProjectDocument) -> ProjectDocument) {
        val document = state.selectedDocument ?: return
        replaceDocument(transform(document))
    }

    private fun replaceDocument(document: ProjectDocument) {
        projectStore.save(document)
        state = state.copy(documents = state.documents.map { current -> if (current.metadata.id == document.metadata.id) document else current })
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

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(downloadReceiver)
        super.onCleared()
    }

    private fun String.isTextFile(): Boolean = lowercase(Locale.ROOT).endsWith(
        ".html"
    ) || lowercase(Locale.ROOT).endsWith(".css") || lowercase(Locale.ROOT).endsWith(".js") || lowercase(Locale.ROOT).endsWith(".json") || lowercase(Locale.ROOT).endsWith(".txt") || lowercase(Locale.ROOT).endsWith(".md")
}

private fun StudioUiState.promptLanguage(): PromptLanguage = when (language) {
    StudioLanguage.Chinese -> PromptLanguage.ZhCn
    StudioLanguage.English -> PromptLanguage.En
}
