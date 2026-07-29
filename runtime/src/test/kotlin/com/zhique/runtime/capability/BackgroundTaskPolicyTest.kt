package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackgroundTaskPolicyTest {
    @Test
    fun `background runtime only accepts declared native notification tasks`() {
        assertEquals("notification", BackgroundTaskPolicy.requireType("notification"))
        assertFailsWith<IllegalArgumentException> { BackgroundTaskPolicy.requireType("run-webview") }
    }
}
