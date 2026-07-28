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

        assertEquals(setOf("geolocation", "bluetooth_le"), analysis.suggestedCapabilities)
        assertTrue(analysis.suggestions.any { it.replacement.contains("weaver.geolocation.getCurrentPosition") })
        assertTrue(analysis.suggestions.any { it.replacement.contains("weaver.bluetooth.requestDevice") })
    }

    @Test
    fun `weaver required comment becomes canonical project capabilities`() {
        val analysis = CodeImportAnalyzer.analyze(
            mapOf(
                "index.html" to "<!-- weaver-required: camera, geolocation, storage, notification, contacts, microphone, clipboard, vibrate, sensor, config -->"
            )
        )

        assertEquals(
            setOf("camera", "geolocation", "storage", "notification", "contacts", "microphone", "clipboard", "haptics", "sensors", "config"),
            analysis.suggestedCapabilities
        )
    }

    @Test
    fun `copyable prompt requires weaver APIs and excludes private secrets`() {
        val prompt = PromptPack.default(PromptLanguage.ZhCn).renderForExternalModel("蓝牙体温计")

        assertTrue(prompt.contains("window.weaver"))
        assertTrue(prompt.contains("私密运行时密钥"))
        assertTrue(prompt.contains("蓝牙体温计"))
    }

    @Test
    fun `Chinese and English prompts share the same Runtime API requirements`() {
        val chinese = PromptPack.default(PromptLanguage.ZhCn).renderForExternalModel("权限测试")
        val english = PromptPack.default(PromptLanguage.En).renderForExternalModel("Permission test")

        listOf("window.weaver.ready", "weaver.camera.capture", "weaver.geolocation.getCurrentPosition", "weaver.storage.writeFile", "weaver.notification.show", "weaver.config.get").forEach { api ->
            assertTrue(chinese.contains(api))
            assertTrue(english.contains(api))
        }
        assertTrue(chinese.contains("中文"))
        assertTrue(english.contains("English"))
    }
}
