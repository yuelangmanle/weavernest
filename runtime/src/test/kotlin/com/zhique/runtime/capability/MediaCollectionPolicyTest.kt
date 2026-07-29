package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaCollectionPolicyTest {
    @Test
    fun `media saves only target one scoped public collection`() {
        assertEquals("images", MediaCollectionPolicy.requireCollection("images"))
        assertEquals("audio", MediaCollectionPolicy.requireCollection("audio"))
        assertFailsWith<IllegalArgumentException> {
            MediaCollectionPolicy.requireCollection("downloads")
        }
    }
}
