package com.zhique.studio.data

import android.content.Context
import com.zhique.core.project.BuildRecord
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import com.zhique.core.project.PreviewDataPersistence
import java.io.File
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class RecycledProject(
    val recycleId: String,
    val projectId: String,
    val displayName: String,
    val deletedAtEpochMillis: Long
)

data class ProjectSnapshot(
    val snapshotId: String,
    val createdAtEpochMillis: Long
)

/**
 * Project workspaces keep code and media as files. Legacy single-file JSON projects are migrated
 * atomically while retaining both the original legacy source and a read-only migration backup.
 */
class ProjectStore(
    private val workspaceRoot: File,
    private val legacyDirectory: File
) {
    constructor(context: Context) : this(
        workspaceRoot = File(context.filesDir, "workspaces"),
        legacyDirectory = File(context.filesDir, "projects")
    )

    private val legacyBackupDirectory = File(workspaceRoot, "legacy-backup")
    private val snapshotsRoot = File(workspaceRoot, "snapshots")
    private val recycleBinRoot = File(workspaceRoot, "recycle-bin")

    init {
        workspaceRoot.mkdirs()
        migrateLegacyProjects()
    }

    fun load(): List<ProjectDocument> {
        migrateLegacyProjects()
        return workspaceRoot.listFiles()
            ?.filter { directory ->
                directory.isDirectory && !directory.name.startsWith(".") &&
                    directory.name != legacyBackupDirectory.name &&
                    directory.name != snapshotsRoot.name &&
                    directory.name != recycleBinRoot.name
            }
            ?.mapNotNull { directory -> runCatching { readWorkspace(directory) }.getOrNull() }
            ?.sortedByDescending { document -> document.buildHistory.lastOrNull()?.createdAtEpochMillis ?: 0L }
            ?: emptyList()
    }

    fun save(document: ProjectDocument) {
        require(document.metadata.id.matches(PROJECT_ID)) { "Invalid project id." }
        val pending = File(workspaceRoot, ".${document.metadata.id}.${UUID.randomUUID()}.pending")
        try {
            writeWorkspace(pending, document)
            replaceWorkspace(document.metadata.id, pending)
        } catch (error: Exception) {
            pending.deleteRecursively()
            throw error
        }
    }

    fun delete(projectId: String) {
        if (!projectId.matches(PROJECT_ID)) return
        File(workspaceRoot, projectId).deleteRecursively()
        File(snapshotsRoot, projectId).deleteRecursively()
    }

    fun moveToRecycleBin(projectId: String): RecycledProject {
        require(projectId.matches(PROJECT_ID)) { "Invalid project id." }
        val workspace = File(workspaceRoot, projectId)
        require(workspace.isDirectory) { "The project workspace no longer exists." }
        val document = readWorkspace(workspace)
        val deletedAt = System.currentTimeMillis()
        val recycleId = "$projectId-$deletedAt-${UUID.randomUUID().toString().take(8)}"
        val destination = File(recycleBinRoot, recycleId)
        recycleBinRoot.mkdirs()
        require(workspace.renameTo(destination)) { "Unable to move the project to the recycle bin." }
        val recycled = RecycledProject(recycleId, projectId, document.metadata.displayName, deletedAt)
        File(destination, RECYCLE_METADATA_FILE).writeText(encodeRecycleMetadata(recycled).toString(), Charsets.UTF_8)
        return recycled
    }

    fun loadRecycleBin(): List<RecycledProject> = recycleBinRoot.listFiles()
        ?.filter(File::isDirectory)
        ?.mapNotNull { directory -> runCatching { decodeRecycleMetadata(directory) }.getOrNull() }
        ?.sortedByDescending(RecycledProject::deletedAtEpochMillis)
        ?: emptyList()

    fun restoreFromRecycleBin(recycleId: String): ProjectDocument {
        require(recycleId.matches(RECYCLE_ID)) { "Invalid recycle bin entry." }
        val directory = File(recycleBinRoot, recycleId)
        require(directory.isDirectory) { "The recycle bin entry no longer exists." }
        val recycled = decodeRecycleMetadata(directory)
        val destination = File(workspaceRoot, recycled.projectId)
        require(!destination.exists()) { "A project with this identity already exists. Delete or rename it before restoring." }
        File(directory, RECYCLE_METADATA_FILE).delete()
        require(directory.renameTo(destination)) { "Unable to restore the project workspace." }
        return readWorkspace(destination)
    }

    fun loadSnapshots(projectId: String): List<ProjectSnapshot> {
        require(projectId.matches(PROJECT_ID)) { "Invalid project id." }
        return File(snapshotsRoot, projectId).listFiles()
            ?.filter(File::isDirectory)
            ?.mapNotNull { directory ->
                directory.name.takeIf { it.matches(SNAPSHOT_ID) }?.let { snapshotId ->
                    ProjectSnapshot(snapshotId, directory.lastModified())
                }
            }
            ?.sortedByDescending(ProjectSnapshot::createdAtEpochMillis)
            ?: emptyList()
    }

    fun restoreSnapshot(projectId: String, snapshotId: String): ProjectDocument {
        require(projectId.matches(PROJECT_ID)) { "Invalid project id." }
        require(snapshotId.matches(SNAPSHOT_ID)) { "Invalid project snapshot id." }
        val snapshot = File(File(snapshotsRoot, projectId), snapshotId)
        require(snapshot.isDirectory) { "The selected project snapshot no longer exists." }
        readWorkspace(snapshot) // Validate before affecting the active workspace.
        val pending = File(workspaceRoot, ".$projectId.${UUID.randomUUID()}.restore.pending")
        try {
            require(snapshot.copyRecursively(pending, overwrite = false)) { "Unable to stage the project snapshot." }
            replaceWorkspace(projectId, pending)
            return readWorkspace(File(workspaceRoot, projectId))
        } catch (error: Exception) {
            pending.deleteRecursively()
            throw error
        }
    }

    private fun migrateLegacyProjects() {
        val candidates = legacyDirectory.listFiles()?.filter { file -> file.isFile && file.extension == "json" }.orEmpty()
        candidates.forEach { legacy ->
            runCatching {
                val raw = legacy.readText(Charsets.UTF_8)
                val document = decodeLegacy(raw)
                val target = File(workspaceRoot, document.metadata.id)
                if (target.isDirectory) return@forEach
                save(document)
                legacyBackupDirectory.mkdirs()
                val backup = File(legacyBackupDirectory, "${document.metadata.id}.json")
                if (!backup.exists()) {
                    backup.writeText(raw, Charsets.UTF_8)
                    backup.setReadOnly()
                }
            }
        }
    }

    private fun writeWorkspace(directory: File, document: ProjectDocument) {
        require(!directory.exists() || directory.deleteRecursively()) { "Unable to prepare project workspace." }
        val filesRoot = File(directory, "files")
        val assetsRoot = File(directory, "assets")
        document.files.forEach { (path, content) -> writeSafely(filesRoot, path, content.toByteArray(Charsets.UTF_8)) }
        document.binaryAssets.forEach { (path, encoded) ->
            val bytes = runCatching { Base64.getDecoder().decode(encoded) }
                .getOrElse { throw IllegalArgumentException("Project asset $path is not valid Base64.") }
            writeSafely(assetsRoot, path, bytes)
        }
        File(directory, METADATA_FILE).writeText(encodeMetadata(document).toString(), Charsets.UTF_8)
    }

    private fun replaceWorkspace(projectId: String, pending: File) {
        val current = File(workspaceRoot, projectId)
        val previous = File(workspaceRoot, ".$projectId.previous")
        previous.deleteRecursively()
        if (current.exists()) {
            createSnapshot(projectId, current)
            require(current.renameTo(previous)) { "Unable to preserve the current project workspace." }
        }
        try {
            require(pending.renameTo(current)) { "Unable to activate the saved project workspace." }
            previous.deleteRecursively()
        } catch (error: Exception) {
            if (!current.exists() && previous.exists()) previous.renameTo(current)
            throw error
        }
    }

    private fun createSnapshot(projectId: String, current: File) {
        val directory = File(snapshotsRoot, projectId).apply { mkdirs() }
        val snapshot = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}")
        require(current.copyRecursively(snapshot, overwrite = false)) { "Unable to create a project snapshot." }
        directory.listFiles()
            ?.sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))
            ?.dropLast(MAX_SNAPSHOTS)
            ?.forEach(File::deleteRecursively)
    }

    private fun readWorkspace(directory: File): ProjectDocument {
        val json = JSONObject(File(directory, METADATA_FILE).readText(Charsets.UTF_8))
        val metadata = decodeMetadata(json)
        val files = json.optStringList("textFiles").associateWith { path ->
            readSafely(File(directory, "files"), path).toString(Charsets.UTF_8)
        }
        val assets = json.optStringList("binaryAssets").associateWith { path ->
            Base64.getEncoder().withoutPadding().encodeToString(readSafely(File(directory, "assets"), path))
        }
        return ProjectDocument(metadata, files, assets, json.optBuildHistory())
    }

    private fun decodeLegacy(raw: String): ProjectDocument {
        val json = JSONObject(raw)
        return ProjectDocument(
            metadata = decodeMetadata(json),
            files = json.optStringMap("files"),
            binaryAssets = json.optStringMap("binaryAssets"),
            buildHistory = json.optBuildHistory()
        )
    }

    private fun encodeRecycleMetadata(recycled: RecycledProject): JSONObject = JSONObject().apply {
        put("recycleId", recycled.recycleId)
        put("projectId", recycled.projectId)
        put("displayName", recycled.displayName)
        put("deletedAt", recycled.deletedAtEpochMillis)
    }

    private fun decodeRecycleMetadata(directory: File): RecycledProject {
        val json = JSONObject(File(directory, RECYCLE_METADATA_FILE).readText(Charsets.UTF_8))
        val recycled = RecycledProject(
            recycleId = json.getString("recycleId"),
            projectId = json.getString("projectId"),
            displayName = json.getString("displayName"),
            deletedAtEpochMillis = json.getLong("deletedAt")
        )
        require(recycled.recycleId == directory.name && recycled.recycleId.matches(RECYCLE_ID)) { "Invalid recycle bin metadata." }
        require(recycled.projectId.matches(PROJECT_ID)) { "Invalid recycled project identity." }
        return recycled
    }

    private fun encodeMetadata(document: ProjectDocument): JSONObject = JSONObject().apply {
        val metadata = document.metadata
        put("id", metadata.id)
        put("displayName", metadata.displayName)
        put("packageName", metadata.packageName)
        put("packageNameLocked", metadata.packageNameLocked)
        put("versionName", metadata.versionName)
        put("versionCode", metadata.versionCode)
        put("lastModifiedAt", metadata.lastModifiedEpochMillis)
        put("promptPackVersion", metadata.promptPackVersion)
        put("previewDataPersistence", metadata.previewDataPersistence.name)
        metadata.iconAssetPath?.let { put("iconAssetPath", it) }
        metadata.signingKeyId?.let { put("signingKeyId", it) }
        metadata.signingCertificateSha256?.let { put("signingCertificateSha256", it) }
        metadata.signingBackupId?.let { put("signingBackupId", it) }
        put("capabilities", JSONArray(metadata.capabilities.sorted()))
        put("textFiles", JSONArray(document.files.keys.sorted()))
        put("binaryAssets", JSONArray(document.binaryAssets.keys.sorted()))
        put("buildHistory", encodeBuildHistory(document.buildHistory))
    }

    private fun decodeMetadata(json: JSONObject): ProjectMetadata = ProjectMetadata(
        id = json.getString("id"),
        displayName = json.getString("displayName"),
        packageName = json.getString("packageName"),
        packageNameLocked = json.optBoolean("packageNameLocked"),
        versionName = json.optString("versionName", "1.0.0"),
        versionCode = json.optInt("versionCode", 0),
        lastModifiedEpochMillis = json.optLong("lastModifiedAt", 0L),
        capabilities = json.optStringSet("capabilities"),
        promptPackVersion = json.optString("promptPackVersion", "1"),
        previewDataPersistence = PreviewDataPersistence.entries.firstOrNull { it.name == json.optString("previewDataPersistence") }
            ?: PreviewDataPersistence.Persistent,
        iconAssetPath = json.optNullableString("iconAssetPath"),
        signingKeyId = json.optNullableString("signingKeyId"),
        signingCertificateSha256 = json.optNullableString("signingCertificateSha256"),
        signingBackupId = json.optNullableString("signingBackupId")
    )

    private fun encodeBuildHistory(records: List<BuildRecord>) = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("versionName", record.versionName)
                put("versionCode", record.versionCode)
                put("createdAt", record.createdAtEpochMillis)
                put("status", record.status)
                put("message", record.message)
                record.artifactFileName?.let { put("artifactFileName", it) }
                record.artifactSha256?.let { put("artifactSha256", it) }
                record.signingKeyId?.let { put("signingKeyId", it) }
            })
        }
    }

    private fun writeSafely(root: File, path: String, bytes: ByteArray) {
        val target = resolveSafely(root, path)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun readSafely(root: File, path: String): ByteArray {
        val target = resolveSafely(root, path)
        require(target.isFile) { "Project workspace is missing $path." }
        return target.readBytes()
    }

    private fun resolveSafely(root: File, rawPath: String): File {
        val path = rawPath.replace('\\', '/')
        require(path.isNotBlank() && !path.startsWith('/') && ':' !in path && path.length <= 240) { "Invalid project workspace path." }
        require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Unsafe project workspace path." }
        val base = root.canonicalFile
        val target = File(base, path).canonicalFile
        require(target.path.startsWith("${base.path}${File.separator}")) { "Project workspace path escapes its root." }
        return target
    }

    private fun JSONObject.optStringSet(key: String): Set<String> = optStringList(key).toSet()
    private fun JSONObject.optStringList(key: String): List<String> {
        val values = optJSONArray(key) ?: return emptyList()
        return buildList { for (index in 0 until values.length()) add(values.getString(index)) }
    }
    private fun JSONObject.optStringMap(key: String): Map<String, String> {
        val values = optJSONObject(key) ?: return emptyMap()
        return buildMap { values.keys().forEach { name -> put(name, values.getString(name)) } }
    }
    private fun JSONObject.optBuildHistory(): List<BuildRecord> {
        val values = optJSONArray("buildHistory") ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                val record = values.getJSONObject(index)
                add(BuildRecord(
                    versionName = record.getString("versionName"),
                    versionCode = record.getInt("versionCode"),
                    createdAtEpochMillis = record.getLong("createdAt"),
                    status = record.getString("status"),
                    message = record.getString("message"),
                    artifactFileName = record.optNullableString("artifactFileName"),
                    artifactSha256 = record.optNullableString("artifactSha256"),
                    signingKeyId = record.optNullableString("signingKeyId")
                ))
            }
        }
    }
    private fun JSONObject.optNullableString(key: String): String? = optString(key, "").trim().ifBlank { null }

    private companion object {
        const val METADATA_FILE = "metadata.json"
        const val RECYCLE_METADATA_FILE = ".zhique-recycle.json"
        const val MAX_SNAPSHOTS = 30
        val PROJECT_ID = Regex("[A-Za-z0-9-]{8,80}")
        val RECYCLE_ID = Regex("[A-Za-z0-9-]{8,80}-[0-9]{13}-[A-Za-z0-9-]{8}")
        val SNAPSHOT_ID = Regex("[0-9]{13}-[A-Za-z0-9-]{36}")
    }
}
