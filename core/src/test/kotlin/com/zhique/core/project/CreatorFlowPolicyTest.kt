package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreatorFlowPolicyTest {
    @Test
    fun `a newer release with an APK is directly downloadable`() {
        assertEquals(
            UpdateAvailability.DownloadAvailable,
            ReleaseUpdatePolicy.availability(
                currentVersion = "0.2.1-alpha",
                releaseTag = "v0.3.0-alpha",
                apkName = "zhique-v0.3.0-alpha.apk"
            )
        )
    }

    @Test
    fun `a newer release without an APK tells the user the package is missing`() {
        assertEquals(
            UpdateAvailability.PackageMissing,
            ReleaseUpdatePolicy.availability(
                currentVersion = "0.2.1-alpha",
                releaseTag = "v0.3.0-alpha",
                apkName = null
            )
        )
    }

    @Test
    fun `the current release is not offered as an update download`() {
        assertEquals(
            UpdateAvailability.UpToDate,
            ReleaseUpdatePolicy.availability(
                currentVersion = "0.2.1-alpha",
                releaseTag = "v0.2.1-alpha",
                apkName = "zhique-v0.2.1-alpha.apk"
            )
        )
    }

    @Test
    fun `mixed HTML is retained as one runnable file while embedded code is reported`() {
        val draft = ExternalCodeImport.prepare(
            projectName = "照片工具",
            source = """
                <!doctype html>
                <html><head><style>body { color: red; }</style></head>
                <body><button>拍照</button><script>console.log('ready')</script></body></html>
            """.trimIndent()
        )

        assertEquals("照片工具", draft.projectName)
        assertTrue(draft.files.containsKey("index.html"))
        assertTrue(draft.detectedParts.contains(ExternalCodePart.EmbeddedCss))
        assertTrue(draft.detectedParts.contains(ExternalCodePart.EmbeddedJavaScript))
    }

    @Test
    fun `Apilot defaults to connection and one selected model without a secret`() {
        assertEquals(
            listOf("connection", "models.default"),
            ApilotPolicy.requestedScopes(includeApiKey = false)
        )
    }

    @Test
    fun `Apilot only requests an API key after explicit approval`() {
        assertEquals(
            listOf("connection", "models.default", "secret.api_key"),
            ApilotPolicy.requestedScopes(includeApiKey = true)
        )
    }
}
