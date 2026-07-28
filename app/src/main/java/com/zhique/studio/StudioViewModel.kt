package com.zhique.studio

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
import com.zhique.core.project.ImportAnalysis
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import com.zhique.core.project.ProjectReleasePolicy
import com.zhique.core.project.ReleaseUpdatePolicy
import com.zhique.studio.data.AiSettings
import com.zhique.studio.data.AiSettingsStore
import com.zhique.studio.data.GitHubReleaseClient
import com.zhique.studio.data.GitHubReleaseInfo
import com.zhique.studio.data.OpenAiCompatibleClient
import com.zhique.studio.data.ProjectStore
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

data class StudioUiState(
    val documents: List<ProjectDocument> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedTab: WorkspaceTab = WorkspaceTab.Ai,
    val language: StudioLanguage = StudioLanguage.Chinese,
    val importAnalysis: ImportAnalysis? = null,
    val aiDraft: String? = null,
    val isAiRequestRunning: Boolean = false,
    val releaseInfo: GitHubReleaseInfo? = null,
    val isUpdateCheckRunning: Boolean = false,
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
    val html: String
)

object TemplateCatalog {
    val all = listOf(
        TemplateDefinition("camera", "拍照识别", "影像与媒体", "拍摄图片并接入识别 API。", setOf("camera", "network"), starterPage("拍照识别", "拍照后可发送到公开识别 API。")),
        TemplateDefinition("music", "音乐播放器", "影像与媒体", "播放本地或网络音频。", setOf("media_audio", "files", "network"), starterPage("音乐播放器", "选择音频文件后开始播放。")),
        TemplateDefinition("album", "相册管理", "影像与媒体", "浏览和管理图片资源。", setOf("media_images", "files"), starterPage("相册管理", "管理图片和相册索引。")),
        TemplateDefinition("files", "文件工具", "文件与数据", "选择、保存和整理文件。", setOf("files"), starterPage("文件工具", "通过文件能力选择和保存文件。")),
        TemplateDefinition("forms", "离线表单", "文件与数据", "离线填写、保存和导出记录。", emptySet(), starterPage("离线表单", "数据将保存在本机。")),
        TemplateDefinition("location", "定位签到", "定位与传感器", "获取位置并保存签到记录。", setOf("location"), starterPage("定位签到", "请求定位后记录当前位置。")),
        TemplateDefinition("ble", "蓝牙 BLE 控制", "蓝牙与近场", "扫描和连接低功耗蓝牙设备。", setOf("bluetooth_le"), starterPage("蓝牙 BLE 控制", "扫描附近的 BLE 设备。")),
        TemplateDefinition("nfc", "NFC 标签工具", "蓝牙与近场", "读取和写入 NFC 标签。", setOf("nfc"), starterPage("NFC 标签工具", "读取附近 NFC 标签。")),
        TemplateDefinition("wifi", "Wi-Fi 网络诊断", "Wi-Fi 与网络", "显示网络状态和 Wi-Fi 限制说明。", setOf("wifi_scan", "network"), starterPage("Wi-Fi 网络诊断", "系统会在需要时要求确认。")),
        TemplateDefinition("hotspot", "局部热点", "Wi-Fi 与网络", "创建受系统限制的局部热点。", setOf("local_hotspot"), starterPage("局部热点", "仅支持 Android 允许的局部热点模式。")),
        TemplateDefinition("api", "API 数据面板", "Wi-Fi 与网络", "请求公开 API 并展示结果。", setOf("network"), starterPage("API 数据面板", "使用公开 API 或运行时私密配置。")),
        TemplateDefinition("notifications", "通知提醒", "系统能力", "创建本地通知和提醒。", setOf("notifications"), starterPage("通知提醒", "通知必须由用户操作触发。")),
        TemplateDefinition("contacts", "联系人助手", "系统能力", "读取联系人并生成本地工具。", setOf("contacts"), starterPage("联系人助手", "只在明确操作后请求联系人权限。")),
        TemplateDefinition("automation", "AI 自动化", "AI 与自动化", "调用用户配置的公共 API 工作流。", setOf("network"), starterPage("AI 自动化", "私密密钥由最终使用者在运行时填写。"))
    )

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

    var state by mutableStateOf(
        StudioUiState(documents = projectStore.load())
    )
        private set

    fun createBlankProject(displayName: String) {
        createProject(displayName, emptySet(), TemplateCatalog.all.first().html)
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

    fun updateFile(path: String, content: String) {
        updateSelected { document -> document.withFile(path, content) }
        analyzeSelectedProject()
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
        val normalized = when {
            fileName.endsWith(".html", true) -> "index.html"
            fileName.endsWith(".css", true) -> "style.css"
            fileName.endsWith(".js", true) -> "app.js"
            else -> fileName
        }
        val files = if (normalized == "index.html") mapOf(normalized to content) else mapOf(
            "index.html" to TemplateCatalog.all.first().html,
            normalized to content
        )
        createProject(fileName.substringBeforeLast('.').ifBlank { "导入项目" }, emptySet(), files["index.html"].orEmpty(), files - "index.html")
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
                ?: TemplateCatalog.all.first().html
            createProject(fileName.substringBeforeLast('.').ifBlank { "导入项目" }, emptySet(), index, files - "index.html", assets)
        }.onFailure { error ->
            state = state.copy(notice = error.message ?: "ZIP 导入失败。")
        }
    }

    fun analyzeSelectedProject() {
        val document = state.selectedDocument ?: return
        state = state.copy(importAnalysis = CodeImportAnalyzer.analyze(document.files))
    }

    fun copyExternalPrompt(): String = state.selectedDocument
        ?.let { document -> com.zhique.core.project.PromptPack.default().renderForExternalModel(document.metadata.displayName) }
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
            runCatching { aiClient.generate(aiSettingsStore.load(), document.metadata.displayName, prompt.trim()) }
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

    fun dismissAiDraft() {
        state = state.copy(aiDraft = null)
    }

    fun clearPreviewData() {
        val projectId = state.selectedDocument?.metadata?.id ?: return
        getApplication<Application>().getSharedPreferences("preview_$projectId", Context.MODE_PRIVATE).edit().clear().apply()
        state = state.copy(notice = "预览数据已清除。")
    }

    fun checkForUpdate() {
        state = state.copy(isUpdateCheckRunning = true, notice = null)
        viewModelScope.launch {
            runCatching { releaseClient.latest(BuildConfig.GITHUB_REPOSITORY) }
                .onSuccess { release ->
                    val newer = ReleaseUpdatePolicy.isNewer(BuildConfig.VERSION_NAME, release.tagName)
                    state = state.copy(
                        releaseInfo = release,
                        isUpdateCheckRunning = false,
                        notice = if (newer) "发现新版本 ${release.tagName}。" else "当前已是最新版本。"
                    )
                }
                .onFailure { error -> state = state.copy(isUpdateCheckRunning = false, notice = error.message ?: "无法检查更新。") }
        }
    }

    fun downloadLatestApk() {
        val release = state.releaseInfo ?: return
        val url = release.apkUrl ?: run {
            state = state.copy(notice = "最新发布没有 APK 安装包。")
            return
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(release.apkName ?: "织雀-${release.tagName}.apk")
            .setDescription("来自 GitHub Releases 的织雀安装包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, release.apkName ?: "织雀-${release.tagName}.apk")
        val manager = getApplication<Application>().getSystemService(DownloadManager::class.java)
        manager.enqueue(request)
        state = state.copy(notice = "已交给系统下载管理器下载更新。")
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

    private fun String.isTextFile(): Boolean = lowercase(Locale.ROOT).endsWith(
        ".html"
    ) || lowercase(Locale.ROOT).endsWith(".css") || lowercase(Locale.ROOT).endsWith(".js") || lowercase(Locale.ROOT).endsWith(".json") || lowercase(Locale.ROOT).endsWith(".txt") || lowercase(Locale.ROOT).endsWith(".md")
}
