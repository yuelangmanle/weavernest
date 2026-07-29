package com.zhique.studio.data

import com.zhique.core.project.CodeImportAnalyzer
import com.zhique.core.project.ImportAnalysis
import java.io.File
import java.util.Base64
import net.lingala.zip4j.ZipFile

data class ZipImportReview(
    val projectName: String,
    val textFiles: Map<String, String>,
    val binaryAssets: Map<String, String>,
    val analysis: ImportAnalysis
) {
    val fileNames: List<String> get() = (textFiles.keys + binaryAssets.keys).sorted()
}

/**
 * Reads user-provided ZIP files into an in-memory review only. The caller must explicitly commit
 * the returned review before a project is created.
 */
object ProjectZipImport {
    const val maxArchiveBytes = 25 * 1024 * 1024
    private const val maxEntries = 512
    private const val maxSingleEntryBytes = 8 * 1024 * 1024
    private const val maxExpandedBytes = 64L * 1024 * 1024

    fun inspect(cacheDir: File, fileName: String, bytes: ByteArray): ZipImportReview {
        require(bytes.isNotEmpty()) { "ZIP 文件为空。" }
        require(bytes.size <= maxArchiveBytes) { "ZIP 文件不能超过 25 MB。" }
        val temporary = File.createTempFile("zhique-import-", ".zip", cacheDir)
        return try {
            temporary.writeBytes(bytes)
            ZipFile(temporary).use { archive ->
                val headers = archive.fileHeaders.filterNot { it.isDirectory }
                require(headers.isNotEmpty()) { "ZIP 中没有可导入的文件。" }
                require(headers.size <= maxEntries) { "ZIP 文件数不能超过 $maxEntries。" }
                val expandedBytes = headers.sumOf { header ->
                    require(header.uncompressedSize in 0..maxSingleEntryBytes.toLong()) {
                        "ZIP 内单个文件不能超过 8 MB。"
                    }
                    header.uncompressedSize
                }
                require(expandedBytes <= maxExpandedBytes) { "ZIP 解压后的总大小不能超过 64 MB。" }

                val textFiles = linkedMapOf<String, String>()
                val binaryAssets = linkedMapOf<String, String>()
                headers.forEach { header ->
                    val path = normalizePath(header.fileName)
                    require(path !in textFiles && path !in binaryAssets) { "ZIP 包含重复文件：$path" }
                    archive.getInputStream(header).use { input ->
                        val data = input.readBytes()
                        require(data.size.toLong() == header.uncompressedSize) { "ZIP 文件读取不完整：$path" }
                        if (path.isTextFile()) textFiles[path] = data.decodeToString()
                        else binaryAssets[path] = Base64.getEncoder().withoutPadding().encodeToString(data)
                    }
                }
                val commonRoot = commonRoot(textFiles.keys + binaryAssets.keys)
                val normalizedText = stripCommonRoot(textFiles, commonRoot)
                val normalizedAssets = stripCommonRoot(binaryAssets, commonRoot)
                val index = normalizedText["index.html"]
                    ?: normalizedText.entries.firstOrNull { it.key.endsWith(".html", ignoreCase = true) }?.value
                    ?: throw IllegalArgumentException("ZIP 必须包含一个 HTML 入口文件。")
                val files = if (normalizedText.containsKey("index.html")) normalizedText else normalizedText + ("index.html" to index)
                ZipImportReview(
                    projectName = fileName.substringBeforeLast('.').trim().ifBlank { "导入项目" },
                    textFiles = files,
                    binaryAssets = normalizedAssets,
                    analysis = CodeImportAnalyzer.analyze(files)
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun normalizePath(raw: String): String {
        val path = raw.replace('\\', '/').trim()
        require(path.isNotBlank() && path.length <= 240) { "ZIP 包含无效文件名。" }
        require(!path.startsWith('/') && !path.contains(":") && path.split('/').none { it == ".." || it.isBlank() }) {
            "ZIP 包含不安全路径：$raw"
        }
        return path
    }

    private fun commonRoot(paths: Collection<String>): String? {
        val root = paths.mapNotNull { it.substringBefore('/', missingDelimiterValue = "").takeIf(String::isNotBlank) }
            .distinct()
            .singleOrNull()
            ?: return null
        val prefix = "$root/"
        return root.takeIf { paths.all { it.startsWith(prefix) } }
    }

    private fun <T> stripCommonRoot(files: Map<String, T>, root: String?): Map<String, T> {
        root ?: return files
        val prefix = "$root/"
        return files.mapKeys { (path, _) -> path.removePrefix(prefix) }
    }
}

private fun String.isTextFile(): Boolean = lowercase().let { name ->
    name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js") ||
        name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".md") ||
        name.endsWith(".svg") || name.endsWith(".xml")
}
