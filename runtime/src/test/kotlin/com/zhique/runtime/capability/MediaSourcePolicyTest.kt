package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaSourcePolicyTest {
    @Test
    fun `audio playback accepts content and HTTPS sources but rejects insecure HTTP`() {
        assertEquals("content", MediaSourcePolicy.requirePlayable("content://media/external/audio/1").scheme)
        assertEquals("https", MediaSourcePolicy.requirePlayable("https://cdn.example.com/audio.mp3").scheme)
        assertFailsWith<IllegalArgumentException> {
            MediaSourcePolicy.requirePlayable("http://cdn.example.com/audio.mp3")
        }
    }
}
