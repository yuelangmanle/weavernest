package com.zhique.runtime.bridge

import org.json.JSONArray
import org.json.JSONObject

data class ParsedRuntimeMessage(
    val request: RuntimeBridgeRequest? = null,
    val requestId: String? = null,
    val error: RuntimeBridgeError? = null
)

object RuntimeMessageCodec {
    const val maxRequestBytes = 32 * 1024

    fun parseRequest(raw: String): ParsedRuntimeMessage {
        if (raw.toByteArray(Charsets.UTF_8).size > maxRequestBytes) {
            return ParsedRuntimeMessage(error = invalid("Runtime request exceeds 32 KB."))
        }
        return try {
            val json = JSONObject(raw)
            val requestId = json.optString("requestId").takeIf(String::isNotBlank)
            val protocolVersion = json.optString("protocolVersion")
            val sessionId = json.optString("sessionId")
            val method = json.optString("method")
            if (protocolVersion.isBlank() || sessionId.isBlank() || requestId == null || method.isBlank()) {
                ParsedRuntimeMessage(requestId = requestId, error = invalid("Runtime request is missing required fields."))
            } else {
                ParsedRuntimeMessage(
                    request = RuntimeBridgeRequest(
                        protocolVersion = protocolVersion,
                        sessionId = sessionId,
                        requestId = requestId,
                        method = method,
                        params = json.optJSONObject("params")?.toKotlinMap() ?: emptyMap()
                    )
                )
            }
        } catch (_: Exception) {
            ParsedRuntimeMessage(error = invalid("Runtime request is not valid JSON."))
        }
    }

    fun responseJson(response: RuntimeBridgeResponse): String = JSONObject().apply {
        put("requestId", response.requestId)
        if (response.error != null) {
            put("error", JSONObject().apply {
                put("code", response.error.code)
                put("message", response.error.message)
                put("capability", response.error.capability)
                put("recoverable", response.error.recoverable)
                put("action", response.error.action)
            })
        } else {
            put("result", JSONObject.wrap(response.result))
        }
    }.toString()

    fun eventJson(event: RuntimeBridgeEvent): String = JSONObject().apply {
        put("subscriptionId", event.subscriptionId)
        put("payload", JSONObject.wrap(event.payload))
    }.toString()

    private fun invalid(message: String): RuntimeBridgeError = RuntimeBridgeError(
        code = "INVALID_ARGUMENT",
        message = message,
        recoverable = false
    )
}

private fun JSONObject.toKotlinMap(): Map<String, Any?> = buildMap {
    this@toKotlinMap.keys().forEach { key -> put(key, this@toKotlinMap.get(key).toKotlinValue()) }
}

private fun Any?.toKotlinValue(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> toKotlinMap()
    is JSONArray -> List(length()) { index -> get(index).toKotlinValue() }
    else -> this
}
