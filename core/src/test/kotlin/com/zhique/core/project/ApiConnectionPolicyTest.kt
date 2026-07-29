package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiConnectionPolicyTest {
    private val valid = ApiConnectionInput(
        endpoint = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        providerId = "deepseek",
        protocolId = "openai_compatible",
        apiKeyLength = 32
    )

    @Test
    fun acceptsHttpsOpenAiCompatibleConnection() {
        assertEquals(ApiConnectionValidation.Valid, ApiConnectionPolicy.validate(valid))
    }

    @Test
    fun rejectsCleartextEndpoint() {
        val result = ApiConnectionPolicy.validate(valid.copy(endpoint = "http://example.com/v1"))
        assertIs<ApiConnectionValidation.Invalid>(result)
    }

    @Test
    fun rejectsEndpointWithEmbeddedCredentials() {
        val result = ApiConnectionPolicy.validate(valid.copy(endpoint = "https://secret@example.com/v1"))
        assertIs<ApiConnectionValidation.Invalid>(result)
    }

    @Test
    fun rejectsUnsafeProviderIdentifier() {
        val result = ApiConnectionPolicy.validate(valid.copy(providerId = "my provider"))
        assertIs<ApiConnectionValidation.Invalid>(result)
    }
}
