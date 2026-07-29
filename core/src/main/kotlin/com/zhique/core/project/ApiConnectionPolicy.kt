package com.zhique.core.project

import java.net.URI

data class ApiConnectionInput(
    val endpoint: String,
    val model: String,
    val providerId: String,
    val protocolId: String,
    val apiKeyLength: Int
)

sealed interface ApiConnectionValidation {
    data object Valid : ApiConnectionValidation
    data class Invalid(val message: String) : ApiConnectionValidation
}

/** Validates connection metadata without ever inspecting or copying API key contents. */
object ApiConnectionPolicy {
    private val identifier = Regex("^[A-Za-z0-9._-]{1,80}$")

    fun validate(input: ApiConnectionInput): ApiConnectionValidation {
        val endpoint = input.endpoint.trim()
        if (endpoint.length !in 12..512) return ApiConnectionValidation.Invalid("接口地址长度无效。")
        val uri = runCatching { URI(endpoint) }.getOrElse {
            return ApiConnectionValidation.Invalid("接口地址不是有效的 HTTPS 地址。")
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            return ApiConnectionValidation.Invalid("接口地址必须使用 HTTPS。")
        }
        if (!uri.userInfo.isNullOrBlank() || uri.fragment != null) {
            return ApiConnectionValidation.Invalid("接口地址不能包含账号、密码或片段。")
        }
        if (input.model.trim().length !in 1..256) return ApiConnectionValidation.Invalid("模型名称不能为空且不能超过 256 个字符。")
        if (!identifier.matches(input.providerId.trim())) return ApiConnectionValidation.Invalid("提供商 ID 只能包含字母、数字、点、下划线和连字符。")
        if (!identifier.matches(input.protocolId.trim())) return ApiConnectionValidation.Invalid("协议 ID 只能包含字母、数字、点、下划线和连字符。")
        if (input.apiKeyLength !in 0..4096) return ApiConnectionValidation.Invalid("API Key 长度无效。")
        return ApiConnectionValidation.Valid
    }
}
