package com.zhique.runtime

import android.content.Context
import android.net.Uri
import com.zhique.core.project.RuntimeProjectDataBackup
import com.zhique.core.project.RuntimeProjectDataBackupCodec
import com.zhique.runtime.capability.RuntimeFilePolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Encrypted interchange for public weaver.data and weaver.storage data. It excludes WebView
 * browser caches and encrypted weaver.config values, so an API key cannot leave the device here.
 */
class RuntimeProjectDataBackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = SharedPreferencesRuntimeDataStore(appContext)

    fun export(projectId: String, password: String): ByteArray {
        val backup = RuntimeProjectDataBackup(
            projectId = requireProjectId(projectId),
            createdAtEpochMillis = System.currentTimeMillis(),
            values = dataStore.snapshot(projectId),
            files = storageFiles(projectId)
        )
        return RuntimeProjectDataBackupCodec.encrypt(backup, password)
    }

    fun exportToUri(projectId: String, password: String, destination: Uri) {
        appContext.contentResolver.openOutputStream(destination)?.use { output ->
            output.write(export(projectId, password))
            output.flush()
        } ?: throw IllegalArgumentException("Android could not open the backup destination.")
    }

    fun restore(projectId: String, password: String, encrypted: ByteArray): RuntimeProjectDataBackup {
        val backup = RuntimeProjectDataBackupCodec.decrypt(encrypted, password)
        require(backup.projectId == requireProjectId(projectId)) { "This backup belongs to a different project." }
        replaceAtomically(projectId, backup)
        return backup
    }

    fun restoreFromUri(projectId: String, password: String, source: Uri): RuntimeProjectDataBackup {
        val bytes = appContext.contentResolver.openInputStream(source)?.use(::readBoundedBackup)
            ?: throw IllegalArgumentException("Android could not read the backup file.")
        return restore(projectId, password, bytes)
    }

    private fun replaceAtomically(projectId: String, backup: RuntimeProjectDataBackup) {
        val root = storageRoot(projectId)
        val parent = requireNotNull(root.parentFile)
        parent.mkdirs()
        val staged = File(parent, "." + projectId + ".restore-" + UUID.randomUUID())
        val previous = File(parent, "." + projectId + ".previous-" + UUID.randomUUID())
        val oldValues = dataStore.snapshot(projectId)
        try {
            require(staged.mkdirs()) { "Unable to stage restored project storage." }
            backup.files().forEach { (path, bytes) -> writeStagedFile(staged, path, bytes) }
            if (root.exists()) require(root.renameTo(previous)) { "Unable to preserve existing project storage." }
            require(staged.renameTo(root)) { "Unable to activate restored project storage." }
            dataStore.replace(projectId, backup.values())
            previous.deleteRecursively()
        } catch (error: Exception) {
            root.deleteRecursively()
            if (previous.exists()) previous.renameTo(root)
            runCatching { dataStore.replace(projectId, oldValues) }
            staged.deleteRecursively()
            throw error
        }
    }

    private fun storageFiles(projectId: String): Map<String, ByteArray> {
        val root = storageRoot(projectId)
        if (!root.isDirectory) return emptyMap()
        val base = root.canonicalFile
        return base.walkTopDown()
            .filter { it.isFile }
            .associate { file ->
                val path = file.canonicalFile.relativeTo(base).invariantSeparatorsPath
                RuntimeFilePolicy.requireRelativePath(path) to file.readBytes()
            }
    }

    private fun writeStagedFile(root: File, rawPath: String, bytes: ByteArray) {
        val path = RuntimeFilePolicy.requireRelativePath(rawPath)
        val base = root.canonicalFile
        val target = File(base, path).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "Backup storage path escapes its project root." }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun storageRoot(projectId: String): File =
        File(appContext.filesDir, "zhique/runtime/" + requireProjectId(projectId) + "/storage").canonicalFile

    private fun readBoundedBackup(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BACKUP_BYTES) { "The runtime-data backup is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun requireProjectId(projectId: String): String {
        require(projectId.matches(Regex("[A-Za-z0-9-]{1,120}"))) { "Invalid project id." }
        return projectId
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    }
}
