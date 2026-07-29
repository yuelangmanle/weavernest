package com.zhique.template

import android.content.Context
import android.util.Base64
import com.zhique.runtime.RuntimeProject
import org.json.JSONObject

data class GeneratedProjectDefinition(
    val displayName: String,
    val capabilities: Set<String>,
    val runtimeProject: RuntimeProject
)

/** Loads only the fixed assets location written by Zhique's template assembler. */
object GeneratedProjectLoader {
    private const val descriptorPath = "weaver/project.json"
    private const val projectRoot = "weaver/project"

    fun load(context: Context): GeneratedProjectDefinition {
        val descriptor = context.assets.open(descriptorPath).bufferedReader().use { reader -> JSONObject(reader.readText()) }
        val projectId = descriptor.optString("projectId").trim().ifBlank { "generated-project" }
        val displayName = descriptor.optString("displayName").trim().ifBlank { context.applicationInfo.loadLabel(context.packageManager).toString() }
        val capabilities = buildSet {
            val entries = descriptor.optJSONArray("capabilities")
            if (entries != null) for (index in 0 until entries.length()) add(entries.getString(index))
        }
        val textFiles = linkedMapOf<String, String>()
        val binaryAssets = linkedMapOf<String, String>()
        context.assets.filesUnder(projectRoot).forEach { path ->
            val relative = path.removePrefix("$projectRoot/")
            if (relative.isNotBlank()) {
                val bytes = context.assets.open(path).use { input -> input.readBytes() }
                if (isTextFile(relative)) textFiles[relative] = bytes.toString(Charsets.UTF_8)
                else binaryAssets[relative] = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        return GeneratedProjectDefinition(
            displayName = displayName,
            capabilities = capabilities,
            runtimeProject = RuntimeProject(projectId, textFiles, binaryAssets)
        )
    }

    private fun android.content.res.AssetManager.filesUnder(path: String): List<String> = buildList {
        fun visit(current: String) {
            val children = list(current).orEmpty()
            if (children.isEmpty()) {
                add(current)
            } else {
                children.forEach { child -> visit("$current/$child") }
            }
        }
        visit(path)
    }

    private fun isTextFile(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in setOf(
        "html", "htm", "css", "js", "mjs", "json", "txt", "svg", "xml", "md", "csv"
    )
}
