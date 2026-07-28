package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildPlannerTest {
    @Test
    fun `P0 permission diagnostic capabilities are known and declare required Android permissions`() {
        val validation = CapabilityRegistry.validate(
            setOf("camera", "geolocation", "storage", "notification", "contacts", "microphone", "clipboard", "haptics", "sensors", "config")
        )

        assertEquals(emptySet(), validation.unknownCapabilities)
        assertTrue("android.permission.CAMERA" in validation.manifestPermissions)
        assertTrue("android.permission.ACCESS_FINE_LOCATION" in validation.manifestPermissions)
        assertTrue("android.permission.POST_NOTIFICATIONS" in validation.manifestPermissions)
        assertTrue("android.permission.RECORD_AUDIO" in validation.manifestPermissions)
        assertTrue("android.permission.VIBRATE" in validation.manifestPermissions)
    }
    @Test
    fun `build plan declares only selected capability permissions`() {
        val plan = BuildPlanner.prepare(
            ProjectMetadata.create("扫描", "app.zhique.scan").copy(
                capabilities = setOf("camera", "network")
            ),
            assetPaths = setOf("index.html", "app.js")
        )

        assertEquals(setOf("android.permission.CAMERA", "android.permission.INTERNET"), plan.manifestPermissions)
        assertTrue(plan.project.packageNameLocked)
        assertEquals(1, plan.project.versionCode)
    }

    @Test
    fun `build plan rejects unknown capabilities`() {
        val project = ProjectMetadata.create("测试", "app.zhique.invalid").copy(
            capabilities = setOf("camera", "system_signature_magic")
        )

        assertFailsWith<BuildValidationException> {
            BuildPlanner.prepare(project, setOf("index.html"))
        }
    }
}
