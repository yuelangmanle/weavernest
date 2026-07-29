package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkRequestPolicyTest {
    @Test
    fun `only HTTPS URLs are accepted for runtime API requests`() {
        assertEquals("https://api.example.com/v1/data", NetworkRequestPolicy.requireHttps("https://api.example.com/v1/data").toString())
        assertFailsWith<IllegalArgumentException> {
            NetworkRequestPolicy.requireHttps("http://api.example.com/v1/data")
        }
    }
}
