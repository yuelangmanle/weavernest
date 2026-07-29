package com.zhique.core.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WeaverManifestParseResult(
    val capabilities: Set<String>,
    val error: String? = null
)

/** Parses the small, versioned project manifest without treating arbitrary source as JSON. */
object WeaverManifest {
    private val parser = Json { isLenient = false; ignoreUnknownKeys = true }

    fun parse(raw: String): WeaverManifestParseResult {
        return runCatching {
            val root = parser.parseToJsonElement(raw).jsonObject
            val capabilities = requireNotNull(root["capabilities"]?.jsonArray) {
                "weaver.json 必须包含 capabilities 数组。"
            }
            val values = linkedSetOf<String>()
            capabilities.forEach { element ->
                val value = element.jsonPrimitive.content.trim()
                require(value.isNotBlank()) { "weaver.json 的 capabilities 不能包含空值。" }
                values += value.lowercase()
            }
            WeaverManifestParseResult(values)
        }.getOrElse { error ->
            WeaverManifestParseResult(emptySet(), "weaver.json 格式错误：${error.message ?: "无法解析"}")
        }
    }
}
