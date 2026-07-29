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

        assertEquals(
            setOf(
                "android.permission.CAMERA",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE"
            ),
            plan.manifestPermissions
        )
        assertEquals(0, plan.sourceProject.versionCode)
        assertTrue(!plan.sourceProject.packageNameLocked)
        assertTrue(plan.candidateProject.packageNameLocked)
        assertEquals(1, plan.candidateProject.versionCode)
    }

    @Test
    fun `preparing a build never consumes project identity until an APK succeeds`() {
        val project = ProjectMetadata.create("离线相册", "app.zhique.album")

        val plan = BuildPlanner.prepare(project, setOf("index.html"))

        assertEquals(project, plan.sourceProject)
        assertEquals(0, project.versionCode)
        assertTrue(!project.packageNameLocked)
        assertEquals(1, plan.candidateProject.versionCode)
        assertTrue(plan.candidateProject.packageNameLocked)
    }

    @Test
    fun `a successful assembly commits exactly the prepared candidate`() {
        val project = ProjectMetadata.create("离线相册", "app.zhique.album")
        val plan = BuildPlanner.prepare(project, setOf("index.html"))

        val committed = BuildPlanner.commitSuccessfulAssembly(project, plan)

        assertEquals(1, committed.versionCode)
        assertTrue(committed.packageNameLocked)
        assertEquals(project.id, committed.id)
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
