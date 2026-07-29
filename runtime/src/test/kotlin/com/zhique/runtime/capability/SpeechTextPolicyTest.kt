package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeechTextPolicyTest {
    @Test
    fun `speech input must be meaningful and bounded`() {
        assertEquals("织雀", SpeechTextPolicy.requireText(" 织雀 "))
        assertFailsWith<IllegalArgumentException> { SpeechTextPolicy.requireText(" ") }
        assertFailsWith<IllegalArgumentException> { SpeechTextPolicy.requireText("a".repeat(10_001)) }
    }
}
