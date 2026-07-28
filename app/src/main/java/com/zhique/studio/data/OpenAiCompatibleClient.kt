package com.zhique.studio.data

import com.zhique.core.project.PromptPack
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAiCompatibleClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun generate(settings: AiSettings, projectName: String, request: String): String =
        withContext(Dispatchers.IO) {
            require(settings.apiKey.isNotBlank()) { "请先在 AI 设置中填写 API Key。" }
            require(settings.endpoint.startsWith("https://")) { "AI 接口地址必须使用 HTTPS。" }
            val body = JSONObject().apply {
                put("model", settings.model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", PromptPack.default().renderForExternalModel(projectName)))
                    put(JSONObject().put("role", "user").put("content", request))
                })
            }
            val httpRequest = Request.Builder()
                .url("${settings.endpoint.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("AI 接口返回 ${response.code}: ${payload.take(300)}")
                JSONObject(payload)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }
}
