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
        assertTrue(analysis.suggestions.any { it.replacement.contains("weaver.bluetooth.scan") })
    }

    @Test
    fun `weaver required comment becomes canonical project capabilities`() {
        val analysis = CodeImportAnalyzer.analyze(
            mapOf(
                "index.html" to "<!-- weaver-required: camera, geolocation, storage, notification, contacts, mic, clipboard, vibrate, sensor, config -->"
            )
        )

        assertEquals(
            setOf("camera", "geolocation", "storage", "notification", "contacts", "microphone", "clipboard", "haptics", "sensors", "config"),
            analysis.suggestedCapabilities
        )
    }

    @Test
    fun `analysis discovers valid runtime calls and reports unknown calls`() {
        val analysis = CodeImportAnalyzer.analyze(
            mapOf(
                "index.html" to """
                    <script>
                      window.weaver.ready();
                      weaver.camera.capture({ quality: 0.8 });
                      weaver.camera.takeEverything();
                      weaver.camera.recordVideo();
                    </script>
                    <!-- weaver-required: camera, imaginary_capability -->
                """.trimIndent()
            )
        )

        assertTrue("camera" in analysis.suggestedCapabilities)
        assertTrue("window.weaver.ready" in analysis.detectedRuntimeMethods)
        assertEquals(listOf(UnknownRuntimeMethod("index.html", "weaver.camera.takeEverything")), analysis.unknownRuntimeMethods)
        assertEquals(listOf(UnavailableRuntimeMethod("index.html", "weaver.camera.recordVideo")), analysis.unavailableRuntimeMethods)
        assertEquals(setOf("imaginary_capability"), analysis.unknownDeclaredCapabilities)
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

        RuntimeApiCatalog.implementedPublicMethodNames.forEach { api ->
            assertTrue(chinese.contains(api))
            assertTrue(english.contains(api))
        }
        assertTrue(chinese.contains("中文"))
        assertTrue(english.contains("English"))
    }
}
