package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ProjectDuplicationPolicyTest {
    @Test
    fun `duplicating a project creates a new unsigned Android application identity`() {
        val source = ProjectDocument(
            metadata = ProjectMetadata.create("蓝牙控制", "app.zhique.ble").copy(
                packageNameLocked = true,
                versionCode = 6,
                signingKeyId = "key-original",
                signingCertificateSha256 = "fingerprint",
                signingBackupId = "backup-key-original"
            ),
            files = mapOf("index.html" to "<h1>BLE</h1>"),
            binaryAssets = mapOf("assets/icon.bin" to "AQI="),
            buildHistory = listOf(BuildRecord("1.0.5", 6, 1L, "succeeded", "signed"))
        )

        val copied = ProjectDuplicationPolicy.duplicate(source, newProjectId = "copied-project-123")

        assertEquals("copied-project-123", copied.metadata.id)
        assertEquals("蓝牙控制 副本", copied.metadata.displayName)
        assertNotEquals(source.metadata.packageName, copied.metadata.packageName)
        assertFalse(copied.metadata.packageNameLocked)
        assertEquals(0, copied.metadata.versionCode)
        assertEquals("1.0.0", copied.metadata.versionName)
        assertNull(copied.metadata.signingKeyId)
        assertNull(copied.metadata.signingCertificateSha256)
        assertNull(copied.metadata.signingBackupId)
        assertEquals(emptyList(), copied.buildHistory)
        assertEquals(source.files, copied.files)
        assertEquals(source.binaryAssets, copied.binaryAssets)
    }
}
