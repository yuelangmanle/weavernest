package com.zhique.core.template

import com.zhique.core.project.CapabilityRegistry
import com.zhique.core.project.PreviewDataPersistence
import com.zhique.core.project.ProjectMetadata
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceCapabilityDiagnosticTemplateTest {
    @Test
    fun `new projects retain preview data unless the creator turns it off`() {
        val project = ProjectMetadata.create("诊断", "app.zhique.diagnostic")

        assertEquals(PreviewDataPersistence.Persistent, project.previewDataPersistence)
        assertEquals(
            PreviewDataPersistence.Ephemeral,
            project.copy(previewDataPersistence = PreviewDataPersistence.Ephemeral).previewDataPersistence
        )
    }

    @Test
    fun `built in diagnostic covers every registered capability and restores results`() {
        val template = DeviceCapabilityDiagnosticTemplate.definition

        assertEquals("device-capability-diagnostic", template.id)
        assertTrue(template.capabilities.containsAll(CapabilityRegistry.all().map { it.id }))
        assertContains(template.html, "await weaver.data.get(RESULTS_KEY)")
        assertContains(template.html, "await weaver.data.set(RESULTS_KEY")
        assertContains(template.html, "not_implemented")
    }

    @Test
    fun `diagnostic names every capability family that needs device verification`() {
        val html = DeviceCapabilityDiagnosticTemplate.definition.html

        listOf(
            "相机与媒体",
            "文件与数据",
            "定位与传感器",
            "蓝牙与近场",
            "Wi-Fi 与网络",
            "系统与辅助功能"
        ).forEach { family -> assertContains(html, family) }
    }
}
