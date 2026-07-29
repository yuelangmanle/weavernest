package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectBuildMetadataTest {
    @Test
    fun `project build identity stores references and never signing material`() {
        val metadata = ProjectMetadata.create("离线相册", "app.zhique.album").copy(
            iconAssetPath = "assets/icon.png",
            signingKeyId = "project-key-42",
            signingCertificateSha256 = "a1b2c3",
            signingBackupId = "backup-42"
        )

        assertEquals("assets/icon.png", metadata.iconAssetPath)
        assertEquals("project-key-42", metadata.signingKeyId)
        assertEquals("a1b2c3", metadata.signingCertificateSha256)
        assertEquals("backup-42", metadata.signingBackupId)
    }

    @Test
    fun `a completed build record identifies its signed APK without containing a secret`() {
        val record = BuildRecord(
            versionName = "1.0.0",
            versionCode = 1,
            createdAtEpochMillis = 100L,
            status = "succeeded",
            message = "已签名 APK",
            artifactFileName = "offline-album-v1.apk",
            artifactSha256 = "abc123",
            signingKeyId = "project-key-42"
        )

        assertEquals("offline-album-v1.apk", record.artifactFileName)
        assertEquals("abc123", record.artifactSha256)
        assertEquals("project-key-42", record.signingKeyId)
        assertTrue(record.toString().contains("project-key-42"))
    }
}
