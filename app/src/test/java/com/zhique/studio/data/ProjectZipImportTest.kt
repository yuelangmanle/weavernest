package com.zhique.studio.data

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectZipImportTest {
    @Test
    fun inspectsProjectWithoutCreatingItAndStripsCommonRoot() {
        val directory = Files.createTempDirectory("zhique-zip-test").toFile()
        try {
            val archive = zip(directory, mapOf(
                "sample/index.html" to "<!-- weaver-required: camera --><h1>Hi</h1>",
                "sample/app.js" to "console.log('ok')",
                "sample/assets/icon.bin" to "asset"
            ))

            val review = ProjectZipImport.inspect(directory, "sample.zip", archive.readBytes())

            assertEquals("sample", review.projectName)
            assertTrue("index.html" in review.textFiles)
            assertTrue("app.js" in review.textFiles)
            assertTrue("assets/icon.bin" in review.binaryAssets)
            assertTrue("camera" in review.analysis.suggestedCapabilities)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsafeZipPath() {
        val directory = Files.createTempDirectory("zhique-zip-test").toFile()
        try {
            val archive = zip(directory, mapOf("../index.html" to "<h1>unsafe</h1>"))

            assertFailsWith<IllegalArgumentException> {
                ProjectZipImport.inspect(directory, "unsafe.zip", archive.readBytes())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun zip(directory: File, entries: Map<String, String>): File {
        val file = File(directory, "input.zip")
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content.encodeToByteArray())
                output.closeEntry()
            }
        }
        return file
    }
}
