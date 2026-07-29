package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyPromptAndTemplateFixtureTest {
    @Test
    fun currentPromptPreservesTheOldWeaverContractAndAddsAVersionedReadyFlow() {
        val legacy = resource("/legacy/prompt-v1-en.txt")
        val current = PromptPack.default(PromptLanguage.En).renderForExternalModel("Legacy migration")

        assertContains(legacy, "window.weaver")
        assertContains(current, "window.weaver")
        assertContains(current, "await window.weaver.ready()")
        assertContains(current, "Runtime ${RuntimeApiCatalog.apiVersion}")
    }

    @Test
    fun legacyTemplateRemainsAnImportableDataOnlyShape() {
        val template = resource("/legacy/template-v1.html")

        assertContains(template, "window.weaver.data.set")
        assertFalse(template.contains("weaver.camera.capture"))
        assertTrue(CodeImportAnalyzer.analyze(mapOf("index.html" to template)).unknownRuntimeMethods.isEmpty())
    }

    private fun resource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path))
        .bufferedReader()
        .use { it.readText() }
}
