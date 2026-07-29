package com.zhique.studio.data

import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectZipExportTest {
    @Test
    fun `project export contains source assets and capability manifest but no creator secrets`() {
        val directory = Files.createTempDirectory("zhique-export").toFile()
        try {
            val document = ProjectDocument(
                metadata = ProjectMetadata.create("相册工具", "app.zhique.album").copy(
                    capabilities = setOf("media_images", "storage"),
                    signingKeyId = "key-must-not-export",
                    signingCertificateSha256 = "fingerprint-must-not-export"
                ),
                files = mapOf("index.html" to "<h1>Album</h1>", "scripts/app.js" to "console.log('ok')"),
                binaryAssets = mapOf("assets/icon.bin" to Base64.getEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3)))
            )

            val archive = ProjectZipExport.write(directory, document)
            val review = ProjectZipImport.inspect(directory, archive.name, archive.readBytes())

            assertTrue(archive.isFile)
            assertEquals(document.files, review.textFiles - "weaver.json")
            assertEquals(document.binaryAssets, review.binaryAssets)
            assertTrue("media_images" in review.analysis.suggestedCapabilities)
            assertTrue("storage" in review.analysis.suggestedCapabilities)
            assertFalse(archive.readText(Charsets.ISO_8859_1).contains("key-must-not-export"))
            assertFalse(archive.readText(Charsets.ISO_8859_1).contains("fingerprint-must-not-export"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
