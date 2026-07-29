package com.zhique.core.stabilization

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectAreaTest {
    @Test
    fun `project workspace has the four stable creator areas`() {
        assertEquals(
            listOf("创作", "运行", "能力", "发布"),
            ProjectArea.entries.map(ProjectArea::chinese)
        )
        assertEquals(
            listOf("Create", "Run", "Capabilities", "Publish"),
            ProjectArea.entries.map(ProjectArea::english)
        )
    }
}
