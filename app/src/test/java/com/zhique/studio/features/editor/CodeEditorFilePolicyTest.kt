package com.zhique.studio.features.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeEditorFilePolicyTest {
    @Test
    fun detectsSupportedEditorLanguages() {
        assertEquals("html", CodeEditorFilePolicy.languageFor("index.html"))
        assertEquals("css", CodeEditorFilePolicy.languageFor("styles/APP.CSS"))
        assertEquals("javascript", CodeEditorFilePolicy.languageFor("main.mjs"))
        assertEquals("json", CodeEditorFilePolicy.languageFor("weaver.json"))
    }

    @Test
    fun protectsOnlyFilesLargerThanTwoMegabytes() {
        assertFalse(CodeEditorFilePolicy.isReadOnly("a".repeat(CodeEditorFilePolicy.maxEditableBytes)))
        assertTrue(CodeEditorFilePolicy.isReadOnly("a".repeat(CodeEditorFilePolicy.maxEditableBytes + 1)))
    }
}
