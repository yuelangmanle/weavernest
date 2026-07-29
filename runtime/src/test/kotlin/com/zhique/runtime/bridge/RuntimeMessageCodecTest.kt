package com.zhique.runtime.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RuntimeMessageCodecTest {
    @Test
    fun `codec preserves the bounded JSON request envelope`() {
        val parsed = RuntimeMessageCodec.parseRequest(
            """{"protocolVersion":"2.0","sessionId":"session-123","requestId":"wr_abcdefgh","method":"camera.capture","params":{"quality":0.7}}"""
        )

        assertNull(parsed.error)
        val request = assertNotNull(parsed.request)
        assertEquals("camera.capture", request.method)
        val quality = request.params.getValue("quality") as Number
        assertEquals(0.7, quality.toDouble())
    }

    @Test
    fun `codec rejects an oversized bridge message before JSON parsing`() {
        val parsed = RuntimeMessageCodec.parseRequest("x".repeat(RuntimeMessageCodec.maxRequestBytes + 1))

        assertEquals("INVALID_ARGUMENT", assertNotNull(parsed.error).code)
    }
}
