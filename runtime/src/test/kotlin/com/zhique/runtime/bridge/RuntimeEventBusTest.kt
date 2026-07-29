package com.zhique.runtime.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuntimeEventBusTest {
    @Test
    fun `an event is delivered through the configured dispatcher`() {
        var received: RuntimeBridgeEvent? = null
        val bus = RuntimeEventBus { task -> task() }
        bus.attach { received = it }

        bus.emit(RuntimeBridgeEvent("sensor-1", mapOf("x" to 1f)))

        assertEquals("sensor-1", received?.subscriptionId)
    }

    @Test
    fun `detached event bus does not deliver a late sensor callback`() {
        var received: RuntimeBridgeEvent? = null
        val queued = mutableListOf<() -> Unit>()
        val bus = RuntimeEventBus { task -> queued += task }
        bus.attach { received = it }

        bus.emit(RuntimeBridgeEvent("sensor-1", "sample"))
        bus.detach()
        queued.forEach { it() }

        assertNull(received)
    }
}
