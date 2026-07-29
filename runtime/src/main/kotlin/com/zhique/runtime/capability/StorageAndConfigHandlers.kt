package com.zhique.runtime.capability

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.RuntimeUiHost
import java.io.File

object MediaCollectionPolicy {
    fun requireCollection(value: String): String {
        require(value in setOf("images", "video", "audio")) { "collection must be images, video, or audio." }
        return value
    }
}

class StorageCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost
) : RuntimeCapabilityHandler {
    override val capabilityId: String = "storage"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf("storage.readFile", "storage.writeFile", "storage.deleteFile", "storage.list", "storage.pickFile", "storage.createFile", "media.save")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "storage.readFile" -> {
            val file = resolve(session, params.requiredString("path"))
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        }

        "storage.writeFile" -> {
            val file = resolve(session, params.requiredString("path"))
            file.parentFile?.mkdirs()
            val content = params.requiredString("text")
            file.writeText(content, Charsets.UTF_8)
            mapOf("path" to RuntimeFilePolicy.requireRelativePath(params.requiredString("path")), "bytes" to content.toByteArray(Charsets.UTF_8).size)
        }

        "storage.deleteFile" -> {
            val file = resolve(session, params.requiredString("path"))
            mapOf("deleted" to (file.exists() && file.delete()))
        }

        "storage.list" -> {
            val directory = resolveDirectory(session, params["path"] as? String)
            mapOf("entries" to directory.listFiles().orEmpty().sortedBy { file -> file.name }.map { file ->
                mapOf("name" to file.name, "directory" to file.isDirectory, "bytes" to file.length())
            })
        }

        "storage.pickFile" -> {
            val uri = uiHost.openDocument(arrayOf("*/*")) ?: throw IllegalStateException("No file was selected.")
            runCatching { appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            mapOf("uri" to uri.toString())
        }

        "storage.createFile" -> {
            val name = params.requiredString("name").take(120)
            val uri = uiHost.createDocument(name) ?: throw IllegalStateException("No file was created.")
            mapOf("uri" to uri.toString())
        }

        "media.save" -> saveMedia(params)

        else -> throw IllegalArgumentException("Unsupported storage method: $method")
    }

    private fun resolve(session: RuntimeSession, path: String): File {
        val relativePath = RuntimeFilePolicy.requireRelativePath(path)
        val root = root(session)
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith("${root.path}${File.separator}")) { "The file path escapes the project sandbox." }
        return target
    }

    private fun resolveDirectory(session: RuntimeSession, path: String?): File {
        val root = root(session)
        if (path.isNullOrBlank()) return root.apply { mkdirs() }
        val target = File(root, RuntimeFilePolicy.requireRelativePath(path)).canonicalFile
        require(target.path.startsWith("${root.path}${File.separator}")) { "The file path escapes the project sandbox." }
        return target.apply { mkdirs() }
    }

    private fun root(session: RuntimeSession) = File(appContext.filesDir, "zhique/runtime/${session.projectId}/storage").canonicalFile

    private fun saveMedia(params: Map<String, Any?>): Map<String, String> {
        val source = Uri.parse(params.requiredString("uri"))
        require(source.scheme in setOf("content", "file")) { "uri must be a content or file URI." }
        val collection = MediaCollectionPolicy.requireCollection(params.requiredString("collection"))
        val resolver = appContext.contentResolver
        val mimeType = (params["mimeType"] as? String).orEmpty().ifBlank { resolver.getType(source) ?: "application/octet-stream" }
        val targetCollection = when (collection) {
            "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val displayName = (params["fileName"] as? String).orEmpty().ifBlank { "zhique_${System.currentTimeMillis()}" }.take(120)
        val destination = requireNotNull(resolver.insert(targetCollection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        })) { "Android could not create the destination media item." }
        return try {
            requireNotNull(resolver.openInputStream(source)) { "Android could not read the source media item." }.use { input ->
                requireNotNull(resolver.openOutputStream(destination)) { "Android could not write the destination media item." }.use { output ->
                    input.copyTo(output)
                }
            }
            mapOf("uri" to destination.toString())
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }
}

@Suppress("DEPRECATION")
class ConfigCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost
) : RuntimeCapabilityHandler {
    override val capabilityId: String = "config"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf("config.get", "config.set", "config.remove")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        val key = params.requiredString("key")
        val preferences = preferences(session.projectId)
        return when (method) {
            "config.get" -> preferences.getString(key, null)
            "config.set" -> {
                if (!uiHost.confirmPrivateConfig(key)) {
                    throw RuntimeCapabilityException(
                        RuntimeBridgeError(
                            code = "USER_CANCELLED",
                            message = "The user cancelled saving the private runtime configuration.",
                            capability = capabilityId,
                            recoverable = true,
                            action = "request_again"
                        )
                    )
                }
                preferences.edit().putString(key, params.requiredString("value")).apply()
                null
            }

            "config.remove" -> {
                preferences.edit().remove(key).apply()
                null
            }
            else -> throw IllegalArgumentException("Unsupported config method: $method")
        }
    }

    private fun preferences(projectId: String) = EncryptedSharedPreferences.create(
        appContext,
        "zhique_runtime_config_$projectId",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun Map<String, Any?>.requiredString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")
