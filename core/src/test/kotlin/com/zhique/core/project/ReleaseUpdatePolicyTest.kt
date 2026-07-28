package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseUpdatePolicyTest {
    @Test
    fun `newer GitHub release is offered while equal release is not`() {
        assertTrue(ReleaseUpdatePolicy.isNewer(currentVersion = "0.2.0", releaseTag = "v0.2.1"))
        assertFalse(ReleaseUpdatePolicy.isNewer(currentVersion = "0.2.0", releaseTag = "0.2.0"))
    }

    @Test
    fun `a stable release is newer than the matching alpha release`() {
        assertTrue(ReleaseUpdatePolicy.isNewer(currentVersion = "0.3.0-alpha", releaseTag = "v0.3.0"))
        assertFalse(ReleaseUpdatePolicy.isNewer(currentVersion = "0.3.0", releaseTag = "v0.3.0-alpha"))
    }

    @Test
    fun `release selects apk asset and ignores checksums`() {
        val asset = ReleaseUpdatePolicy.selectApkAsset(
            listOf("织雀-v0.2.1.sha256", "织雀-v0.2.1.apk", "source.zip")
        )

        assertEquals("织雀-v0.2.1.apk", asset)
    }
}
