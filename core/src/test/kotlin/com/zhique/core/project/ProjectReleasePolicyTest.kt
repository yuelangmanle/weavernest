package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectReleasePolicyTest {
    @Test
    fun `first export locks package name and starts at version code one`() {
        val project = ProjectMetadata.create(
            displayName = "拍照识别",
            packageName = "app.zhique.camera"
        )

        val exported = ProjectReleasePolicy.prepareExport(project)

        assertTrue(exported.packageNameLocked)
        assertEquals(1, exported.versionCode)
        assertEquals("1.0.0", exported.versionName)
    }

    @Test
    fun `later export increments version code without changing display version`() {
        val exported = ProjectReleasePolicy.prepareExport(
            ProjectMetadata.create("音乐播放器", "app.zhique.music")
        )

        val updated = ProjectReleasePolicy.prepareExport(
            exported.copy(versionName = "1.0.1")
        )

        assertEquals(2, updated.versionCode)
        assertEquals("1.0.1", updated.versionName)
    }

    @Test
    fun `locked package name cannot be changed`() {
        val exported = ProjectReleasePolicy.prepareExport(
            ProjectMetadata.create("蓝牙控制", "app.zhique.ble")
        )

        assertFailsWith<PackageNameLockedException> {
            ProjectReleasePolicy.changePackageName(exported, "app.zhique.newble")
        }
    }

    @Test
    fun `capability registry declares user selected special file access permission`() {
        val validation = CapabilityRegistry.validate(setOf("camera", "manage_external_storage"))

        assertTrue(validation.isAllowed)
        assertEquals(
            setOf("android.permission.CAMERA", "android.permission.MANAGE_EXTERNAL_STORAGE"),
            validation.manifestPermissions
        )
        assertTrue("manage_external_storage" in validation.restrictedCapabilities)
    }
}
