package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BuildLifecyclePolicyTest {
    @Test
    fun `project content fingerprint is stable across map ordering`() {
        val first = ProjectDocument(
            metadata = ProjectMetadata.create("工具", "app.zhique.tool"),
            files = linkedMapOf("index.html" to "<h1>织雀</h1>", "app.js" to "console.log(1)"),
            binaryAssets = linkedMapOf("assets/logo.bin" to "AQID")
        )
        val reordered = first.copy(
            files = linkedMapOf("app.js" to "console.log(1)", "index.html" to "<h1>织雀</h1>"),
            binaryAssets = linkedMapOf("assets/logo.bin" to "AQID")
        )

        assertEquals(first.contentFingerprint(), reordered.contentFingerprint())
    }

    @Test
    fun `project content fingerprint changes when a file changes`() {
        val document = ProjectDocument(
            metadata = ProjectMetadata.create("工具", "app.zhique.tool"),
            files = mapOf("index.html" to "<h1>first</h1>")
        )

        assertNotEquals(document.contentFingerprint(), document.withFile("index.html", "<h1>second</h1>").contentFingerprint())
    }

    @Test
    fun `successful build cannot commit after project files changed`() {
        val document = ProjectDocument(
            metadata = ProjectMetadata.create("工具", "app.zhique.tool"),
            files = mapOf("index.html" to "<h1>first</h1>")
        )
        val plan = BuildPlanner.prepare(document)

        assertFailsWith<IllegalArgumentException> {
            BuildPlanner.commitSuccessfulAssembly(document.withFile("index.html", "<h1>second</h1>"), plan)
        }
    }

    @Test
    fun `successful build commits candidate only when source snapshot still matches`() {
        val document = ProjectDocument(
            metadata = ProjectMetadata.create("工具", "app.zhique.tool"),
            files = mapOf("index.html" to "<h1>first</h1>")
        )
        val plan = BuildPlanner.prepare(document)

        val completed = BuildPlanner.commitSuccessfulAssembly(document, plan)

        assertEquals(1, completed.metadata.versionCode)
        assertTrue(completed.metadata.packageNameLocked)
    }
}
