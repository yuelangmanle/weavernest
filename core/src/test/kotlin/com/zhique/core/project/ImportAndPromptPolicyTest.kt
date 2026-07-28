package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportAndPromptPolicyTest {
    @Test
    fun `import analysis proposes weaver conversions for browser device APIs`() {
        val analysis = CodeImportAnalyzer.analyze(
            mapOf(
                "index.html" to "<button id=\"locate\">Locate</button>",
                "app.js" to "navigator.geolocation.getCurrentPosition(success); navigator.bluetooth.requestDevice({});"
            )
        )

        assertEquals(setOf("location", "bluetooth_le"), analysis.suggestedCapabilities)
        assertTrue(analysis.suggestions.any { it.replacement.contains("weaver.location.getCurrent") })
        assertTrue(analysis.suggestions.any { it.replacement.contains("weaver.bluetooth.requestDevice") })
    }

    @Test
    fun `copyable prompt requires weaver APIs and excludes private secrets`() {
        val prompt = PromptPack.default().renderForExternalModel("蓝牙体温计")

        assertTrue(prompt.contains("window.weaver"))
        assertTrue(prompt.contains("private runtime secrets"))
        assertTrue(prompt.contains("蓝牙体温计"))
    }
}
