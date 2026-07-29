package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TemplateAssemblyPolicyTest {
    @Test
    fun `project assets are confined below the fixed template project root`() {
        assertEquals(
            "assets/weaver/project/scripts/app.js",
            TemplateAssemblyPolicy.projectAssetPath("scripts/app.js")
        )
    }

    @Test
    fun `template assembly rejects paths that could replace native APK entries`() {
        listOf("../classes.dex", "/AndroidManifest.xml", "assets/../../res/raw/x").forEach { path ->
            assertFailsWith<IllegalArgumentException> { TemplateAssemblyPolicy.projectAssetPath(path) }
        }
    }

    @Test
    fun `signed artifact name is derived from the locked package and candidate version`() {
        val project = ProjectMetadata.create("工具", "app.zhique.camera").copy(versionName = "1.2.0", versionCode = 8)

        assertEquals("app.zhique.camera-v1.2.0-8.apk", TemplateAssemblyPolicy.artifactFileName(project))
    }
}
