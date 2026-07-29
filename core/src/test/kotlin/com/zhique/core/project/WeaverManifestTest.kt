package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeaverManifestTest {
    @Test
    fun parsesCapabilitiesFromStructuredManifest() {
        val result = WeaverManifest.parse("""{"runtimeApi":"2.0","capabilities":["camera","network"]}""")

        assertEquals(setOf("camera", "network"), result.capabilities)
    }

    @Test
    fun importAnalysisIncludesManifestCapabilitiesAndErrors() {
        val analysis = CodeImportAnalyzer.analyze(
            mapOf(
                "weaver.json" to """{"capabilities":["camera","unknown"]}""",
                "nested/weaver.json" to "{}"
            )
        )

        assertTrue("camera" in analysis.suggestedCapabilities)
        assertEquals(setOf("unknown"), analysis.unknownDeclaredCapabilities)
        assertEquals(1, analysis.manifestErrors.size)
    }
}
