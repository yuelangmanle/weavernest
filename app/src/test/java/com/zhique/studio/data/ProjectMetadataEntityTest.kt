package com.zhique.studio.data

import com.zhique.core.project.BuildRecord
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import com.zhique.core.project.PreviewDataPersistence
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectMetadataEntityTest {
    @Test
    fun workspaceDocumentProducesARoomIndexWithTheFullReleaseIdentity() {
        val document = ProjectDocument(
            metadata = ProjectMetadata.create("Room 索引", "app.zhique.room").copy(
                packageNameLocked = true,
                versionName = "1.2.3",
                versionCode = 12,
                capabilities = setOf("camera", "network"),
                previewDataPersistence = PreviewDataPersistence.Ephemeral,
                signingKeyId = "key-42"
            ),
            files = mapOf("index.html" to "<h1>not indexed as source</h1>"),
            buildHistory = listOf(BuildRecord("1.2.3", 12, 123L, "succeeded", "built", "app.apk", "abc", "key-42"))
        )

        val entity = document.toMetadataEntity()

        assertEquals(document.metadata, entity.toMetadata())
        assertEquals("key-42", entity.signingKeyId)
        assertEquals(12, entity.versionCode)
        assertEquals(1, org.json.JSONArray(entity.buildHistoryJson).length())
    }
}
