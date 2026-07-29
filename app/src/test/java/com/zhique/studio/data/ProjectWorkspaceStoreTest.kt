package com.zhique.studio.data

import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.ProjectMetadata
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectWorkspaceStoreTest {
    @Test
    fun `workspace store keeps text and binary project files outside metadata JSON`() {
        val root = Files.createTempDirectory("zhique-workspace").toFile()
        try {
            val store = ProjectStore(File(root, "workspaces"), File(root, "legacy"))
            val document = ProjectDocument(
                metadata = ProjectMetadata.create("离线相册", "app.zhique.album"),
                files = mapOf("index.html" to "<h1>织雀</h1>", "scripts/app.js" to "console.log(1)"),
                binaryAssets = mapOf("assets/logo.bin" to Base64.getEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3)))
            )

            store.save(document)
            val workspace = File(root, "workspaces/${document.metadata.id}")

            assertTrue(File(workspace, "files/index.html").isFile)
            assertTrue(File(workspace, "assets/assets/logo.bin").isFile)
            assertTrue(!File(workspace, "metadata.json").readText().contains("console.log(1)"))
            assertEquals(document.files, store.load().single().files)
            assertEquals(document.binaryAssets, store.load().single().binaryAssets)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `legacy project JSON migrates once while retaining its original recovery file`() {
        val root = Files.createTempDirectory("zhique-workspace").toFile()
        try {
            val legacy = File(root, "legacy").apply { mkdirs() }
            File(legacy, "legacy-id.json").writeText(
                """{"id":"legacy-id","displayName":"旧项目","packageName":"app.zhique.legacy","files":{"index.html":"<p>old</p>"},"binaryAssets":{},"capabilities":[],"buildHistory":[]}"""
            )

            val store = ProjectStore(File(root, "workspaces"), legacy)
            val migrated = store.load().single()

            assertEquals("legacy-id", migrated.metadata.id)
            assertEquals("<p>old</p>", migrated.files.getValue("index.html"))
            assertTrue(File(root, "workspaces/legacy-backup/legacy-id.json").isFile)
            assertTrue(File(legacy, "legacy-id.json").isFile)
            assertEquals(listOf(migrated), store.load())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `legacy fixture migrates its source and current metadata defaults without deleting the source file`() {
        val root = Files.createTempDirectory("zhique-legacy-fixture").toFile()
        try {
            val legacy = File(root, "legacy").apply { mkdirs() }
            val fixture = requireNotNull(javaClass.getResourceAsStream("/legacy/project-v1.json")).bufferedReader().use { it.readText() }
            val source = File(legacy, "legacy-fixture-id.json").apply { writeText(fixture) }

            val migrated = ProjectStore(File(root, "workspaces"), legacy).load().single()

            assertEquals("legacy-fixture-id", migrated.metadata.id)
            assertEquals("1", migrated.metadata.promptPackVersion)
            assertEquals("<!doctype html><html><body><button onclick=\"window.weaver.data.set('saved','yes')\">Save</button></body></html>", migrated.files.getValue("index.html"))
            assertTrue(source.isFile)
            assertTrue(File(root, "workspaces/legacy-backup/legacy-fixture-id.json").readText().contains("Legacy Fixture"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `workspace store retains at most thirty prior project snapshots`() {
        val root = Files.createTempDirectory("zhique-workspace").toFile()
        try {
            val store = ProjectStore(File(root, "workspaces"), File(root, "legacy"))
            val initial = ProjectDocument(
                metadata = ProjectMetadata.create("快照", "app.zhique.snapshot"),
                files = mapOf("index.html" to "0")
            )
            store.save(initial)
            repeat(35) { revision -> store.save(initial.withFile("index.html", "${revision + 1}")) }

            val snapshots = File(root, "workspaces/snapshots/${initial.metadata.id}").listFiles().orEmpty()

            assertEquals(30, snapshots.size)
            assertEquals("35", store.load().single().files.getValue("index.html"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a retained workspace snapshot can be restored without losing project identity`() {
        val root = Files.createTempDirectory("zhique-workspace").toFile()
        try {
            val store = ProjectStore(File(root, "workspaces"), File(root, "legacy"))
            val initial = ProjectDocument(
                metadata = ProjectMetadata.create("恢复快照", "app.zhique.snapshotrestore"),
                files = mapOf("index.html" to "initial")
            )
            store.save(initial)
            store.save(initial.withFile("index.html", "changed"))
            val snapshot = store.loadSnapshots(initial.metadata.id).single()

            val restored = store.restoreSnapshot(initial.metadata.id, snapshot.snapshotId)

            assertEquals(initial.metadata.id, restored.metadata.id)
            assertEquals("initial", restored.files.getValue("index.html"))
            assertEquals("initial", store.load().single().files.getValue("index.html"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `deleted workspace moves to a recoverable recycle bin and can be restored`() {
        val root = Files.createTempDirectory("zhique-workspace").toFile()
        try {
            val store = ProjectStore(File(root, "workspaces"), File(root, "legacy"))
            val document = ProjectDocument(
                metadata = ProjectMetadata.create("待恢复项目", "app.zhique.restore"),
                files = mapOf("index.html" to "<p>recover me</p>")
            )
            store.save(document)

            store.moveToRecycleBin(document.metadata.id)

            assertTrue(store.load().isEmpty())
            val recycled = store.loadRecycleBin().single()
            assertEquals(document.metadata.id, recycled.projectId)
            assertEquals("待恢复项目", recycled.displayName)

            store.restoreFromRecycleBin(recycled.recycleId)

            assertEquals(listOf(document), store.load())
            assertTrue(store.loadRecycleBin().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
