package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SystemUriPolicyTest {
    @Test
    fun `system links are restricted to browser safe schemes`() {
        assertEquals("https", SystemUriPolicy.requireWebUri("https://zhique.local/docs").scheme)
        assertFailsWith<IllegalArgumentException> {
            SystemUriPolicy.requireWebUri("intent://untrusted")
        }
    }
}
