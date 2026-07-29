package com.zhique.runtime.bridge

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeOriginPolicyTest {
    @Test
    fun acceptsOnlyActiveProjectTopLevelOrigin() {
        assertTrue(RuntimeOriginPolicy.accepts("https://project.zhique.local", "https://project.zhique.local", true))
        assertFalse(RuntimeOriginPolicy.accepts("https://remote.example", "https://project.zhique.local", true))
        assertFalse(RuntimeOriginPolicy.accepts("https://project.zhique.local", "https://project.zhique.local", false))
        assertFalse(RuntimeOriginPolicy.accepts("https://project.zhique.local", null, true))
    }
}
