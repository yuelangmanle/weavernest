package com.zhique.studio.data

import android.content.Context
import com.zhique.core.project.BuildRecord
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectStore(context: Context) {
    private val directory = File(context.filesDir, "projects").apply { mkdirs() }

    fun load(): List<ProjectDocument> = directory.listFiles()
        ?.filter { it.extension == "json" }
        ?.mapNotNull { file -> runCatching { decode(file.readText()) }.getOrNull() }
        ?.sortedByDescending { document -> document.buildHistory.lastOrNull()?.createdAtEpochMillis ?: 0L }
        ?: emptyList()

    fun save(document: ProjectDocument) {
        File(directory, "${document.metadata.id}.json").writeText(encode(document).toString())
    }

    fun delete(projectId: String) {
        File(directory, "$projectId.json").delete()
    }

    private fun encode(document: ProjectDocument): JSONObject = JSONObject().apply {
        put("id", document.metadata.id)
        put("displayName", document.metadata.displayName)
        put("packageName", document.metadata.packageName)
        put("packageNameLocked", document.metadata.packageNameLocked)
        put("versionName", document.metadata.versionName)
        put("versionCode", document.metadata.versionCode)
        put("promptPackVersion", document.metadata.promptPackVersion)
        put("capabilities", JSONArray(document.metadata.capabilities.toList()))
        put("files", JSONObject().apply { document.files.forEach(::put) })
        put("binaryAssets", JSONObject().apply { document.binaryAssets.forEach(::put) })
        put("buildHistory", JSONArray().apply {
            document.buildHistory.forEach { record ->
                put(JSONObject().apply {
                    put("versionName", record.versionName)
                    put("versionCode", record.versionCode)
                    put("createdAt", record.createdAtEpochMillis)
                    put("status", record.status)
                    put("message", record.message)
                })
            }
        })
    }

    private fun decode(raw: String): ProjectDocument {
        val json = JSONObject(raw)
        val metadata = ProjectMetadata(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            packageName = json.getString("packageName"),
            packageNameLocked = json.optBoolean("packageNameLocked"),
            versionName = json.optString("versionName", "1.0.0"),
            versionCode = json.optInt("versionCode", 0),
            capabilities = json.optStringSet("capabilities"),
            promptPackVersion = json.optString("promptPackVersion", "1")
        )
        return ProjectDocument(
            metadata = metadata,
            files = json.optStringMap("files"),
            binaryAssets = json.optStringMap("binaryAssets"),
            buildHistory = json.optBuildHistory()
        )
    }

    private fun JSONObject.optStringSet(key: String): Set<String> {
        val values = optJSONArray(key) ?: return emptySet()
        return buildSet { for (index in 0 until values.length()) add(values.getString(index)) }
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
                add(
                    BuildRecord(
                        versionName = record.getString("versionName"),
                        versionCode = record.getInt("versionCode"),
                        createdAtEpochMillis = record.getLong("createdAt"),
                        status = record.getString("status"),
                        message = record.getString("message")
                    )
                )
            }
        }
    }
}
