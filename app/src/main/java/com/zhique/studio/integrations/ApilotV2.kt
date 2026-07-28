package com.zhique.studio.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.zhique.core.project.ApilotPolicy
import com.zhique.studio.data.AiSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ApilotProfile(
    val endpoint: String,
    val model: String,
    val providerId: String,
    val protocolId: String,
    val apiKey: String?
)

object ApilotV2 {
    const val packageName = "com.example.api_manager"
    const val repositoryUrl = "https://github.com/yuelangmanle/Apilot"

    private const val importAction = "com.apilot.intent.action.IMPORT_API_CONFIGS"
    private const val pickAction = "com.apilot.intent.action.PICK_API_CONFIG"
    private const val importMimeType = "application/vnd.apilot.api-configs+json"
    private const val extraApiConfigJson = "com.apilot.extra.API_CONFIG_JSON"
    private const val extraSourceName = "com.apilot.extra.SOURCE_NAME"
    private const val extraRequestId = "com.apilot.extra.REQUEST_ID"
    private const val extraSchemaVersion = "com.apilot.extra.SCHEMA_VERSION"
    private const val extraRequestedScopes = "com.apilot.extra.REQUESTED_SCOPES"
    private const val extraReturnTransport = "com.apilot.extra.RETURN_TRANSPORT"

    fun isAvailable(context: Context): Boolean = createPickIntent(false).resolveActivity(context.packageManager) != null

    fun createImportProbeIntent(): Intent = Intent(importAction).apply {
        setPackage(packageName)
        type = importMimeType
    }

    fun createPickIntent(includeApiKey: Boolean): Intent = Intent(pickAction).apply {
        setPackage(packageName)
        putExtra(extraSourceName, "织雀 Zhique")
        putExtra(extraRequestId, UUID.randomUUID().toString())
        putExtra(extraSchemaVersion, 2)
        putStringArrayListExtra(extraRequestedScopes, ArrayList(ApilotPolicy.requestedScopes(includeApiKey)))
        putExtra(extraReturnTransport, "auto")
    }

    fun parsePickResult(context: Context, data: Intent): ApilotProfile {
        val raw = data.getStringExtra(extraApiConfigJson) ?: data.data?.let { uri ->
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } ?: error("Apilot 没有返回 API 方案，请重新选择。")
        val result = JSONObject(raw)
        require(result.optInt("schemaVersion") == 2) { "Apilot 返回了不支持的方案版本。" }
        val profile = result.getJSONObject("apiProfile")
        val connection = profile.getJSONObject("connection")
        val model = profile.optJSONObject("models")?.optString("selectedModel").orEmpty()
        require(connection.optString("baseUrl").isNotBlank()) { "Apilot 方案缺少 API 地址。" }
        require(model.isNotBlank()) { "Apilot 方案缺少默认模型。" }
        return ApilotProfile(
            endpoint = connection.getString("baseUrl").trimEnd('/'),
            model = model,
            providerId = profile.optJSONObject("provider")?.optString("id").orEmpty().ifBlank { "custom" },
            protocolId = profile.optJSONObject("protocol")?.optString("id").orEmpty().ifBlank { "openai_compatible" },
            apiKey = profile.optJSONObject("secrets")?.optString("apiKey")?.takeIf { it.isNotBlank() }
        )
    }

    fun createExportIntent(context: Context, settings: AiSettings, includeApiKey: Boolean): Intent {
        val profile = JSONObject().apply {
            put("connection", JSONObject().apply {
                put("name", "织雀 ${settings.providerId} 方案")
                put("baseUrl", settings.endpoint.trimEnd('/'))
                put("environment", "production")
            })
            put("provider", JSONObject().put("id", settings.providerId.ifBlank { "custom" }))
            put("protocol", JSONObject().put("id", settings.protocolId.ifBlank { "openai_compatible" }))
            put("models", JSONObject().put("selectedModel", settings.model))
            if (includeApiKey && settings.apiKey.isNotBlank()) {
                put("secrets", JSONObject().put("apiKey", settings.apiKey))
            }
            put("origin", JSONObject().put("appName", "织雀 Zhique"))
        }
        val payload = JSONObject().apply {
            put("schemaVersion", 2)
            put("source", JSONObject().apply {
                put("appName", "织雀 Zhique")
                put("packageName", context.packageName)
            })
            put("apiProfiles", JSONArray().put(profile))
        }.toString()
        val uri = writeOneTimePayload(context, payload)
        return Intent(importAction).apply {
            setPackage(packageName)
            setDataAndType(uri, importMimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(extraSourceName, "织雀 Zhique")
            putExtra(extraRequestId, UUID.randomUUID().toString())
        }
    }

    private fun writeOneTimePayload(context: Context, payload: String): Uri {
        val exportDirectory = File(context.cacheDir, "apilot-export").apply { mkdirs() }
        exportDirectory.listFiles()?.forEach { it.delete() }
        val payloadFile = File(exportDirectory, "profile-${UUID.randomUUID()}.json").apply { writeText(payload) }
        return FileProvider.getUriForFile(context, "${context.packageName}.apilot", payloadFile)
    }
}
