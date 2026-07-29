package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeLogRedactorTest {
    @Test
    fun `runtime logs redact private credentials and bearer values`() {
        val log = RuntimeLogRedactor.redact("request failed apiKey=sk-secret-value Authorization: Bearer token-value")

        assertFalse(log.contains("sk-secret-value"))
        assertFalse(log.contains("token-value"))
        assertTrue(log.contains("[redacted]"))
    }

    @Test
    fun `runtime logs redact locations data URLs and content URIs`() {
        val log = RuntimeLogRedactor.redact("at 31.230416, 121.473701 data:image/png;base64,AAAA content://contacts/42")

        assertFalse(log.contains("31.230416"))
        assertFalse(log.contains("content://contacts/42"))
        assertFalse(log.contains("data:image/png"))
        assertTrue(log.contains("[redacted-location]"))
    }
}
