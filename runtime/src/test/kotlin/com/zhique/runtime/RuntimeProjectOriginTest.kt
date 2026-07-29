package com.zhique.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RuntimeProjectOriginTest {
    @Test
    fun `project origin is stable and isolated by project id`() {
        assertEquals(
            "https://projectabc.zhique.local",
            runtimeOriginFor("Project-ABC")
        )
        assertNotEquals(
            runtimeOriginFor("project-a"),
            runtimeOriginFor("project-b")
        )
    }
}
