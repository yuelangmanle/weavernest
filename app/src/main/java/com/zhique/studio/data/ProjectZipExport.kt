package com.zhique.studio.data

import com.zhique.core.project.ProjectDocument
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Exports only portable project source. Creator settings, preview data, builds, and signing identity stay local. */
object ProjectZipExport {
    fun write(directory: File, document: ProjectDocument): File {
        directory.mkdirs()
        require(directory.isDirectory) { "无法创建项目导出目录。" }
        val fileName = "zhique-project-${document.metadata.id}-${System.currentTimeMillis()}.zip"
        val destination = File(directory, fileName)
        val pending = File(directory, ".$fileName.pending")
        try {
            ZipOutputStream(pending.outputStream()).use { output ->
                val textFiles = document.files.toMutableMap().apply {
                    put("weaver.json", capabilityManifest(document))
                }
                textFiles.toSortedMap().forEach { (path, content) ->
                    output.writeEntry(path, content.toByteArray(Charsets.UTF_8))
                }
                document.binaryAssets.toSortedMap().forEach { (path, encoded) ->
                    val bytes = runCatching { Base64.getDecoder().decode(encoded) }
                        .getOrElse { throw IllegalArgumentException("项目资源 $path 不是有效的二进制数据。") }
                    output.writeEntry(path, bytes)
                }
            }
            require(pending.renameTo(destination)) { "无法完成项目 ZIP 导出。" }
            return destination
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
    }

    private fun capabilityManifest(document: ProjectDocument): String = JSONObject().apply {
        put("runtimeApi", "2.0")
        put("capabilities", JSONArray(document.metadata.capabilities.sorted()))
    }.toString(2)

    private fun ZipOutputStream.writeEntry(path: String, bytes: ByteArray) {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && ':' !in normalized && normalized.length <= 240) {
            "项目导出包含无效路径：$path"
        }
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "项目导出包含不安全路径：$path"
        }
        putNextEntry(ZipEntry(normalized))
        write(bytes)
        closeEntry()
    }
}
