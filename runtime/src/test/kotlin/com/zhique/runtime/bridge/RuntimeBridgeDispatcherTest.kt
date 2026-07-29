package com.zhique.runtime.bridge

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RuntimeBridgeDispatcherTest {
    private val session = RuntimeSession(
        id = "session-123",
        projectId = "project-123",
        selectedCapabilities = setOf("camera"),
        runtimeName = "preview",
        androidApi = 35
    )

    @Test
    fun `runtime ready returns a session scoped handshake`() = runBlocking {
        val response = RuntimeBridgeDispatcher(InMemoryRuntimeDataStore()).dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "runtime.ready"),
            session
        )

        assertNull(response.error)
        assertEquals("2.0", (response.result as Map<*, *>) ["apiVersion"])
        assertEquals("project-123", (response.result as Map<*, *>) ["projectId"])
    }

    @Test
    fun `native calls require that the project selected the capability`() = runBlocking {
        val response = RuntimeBridgeDispatcher(InMemoryRuntimeDataStore()).dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "geolocation.getCurrentPosition"),
            session
        )

        assertEquals("CAPABILITY_NOT_SELECTED", assertNotNull(response.error).code)
    }

    @Test
    fun `capability request delegates only selected capabilities to the runtime permission controller`() = runBlocking {
        var requested: String? = null
        val dispatcher = RuntimeBridgeDispatcher(
            dataStore = InMemoryRuntimeDataStore(),
            capabilityControls = object : RuntimeCapabilityControls {
                override suspend fun request(capabilityId: String, session: RuntimeSession): Any? {
                    requested = capabilityId
                    return mapOf("id" to capabilityId, "state" to "granted")
                }

                override suspend fun openSettings(capabilityId: String, session: RuntimeSession): Any? = error("unused")
            }
        )

        val response = dispatcher.dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "capabilities.request", mapOf("id" to "camera")),
            session
        )

        assertNull(response.error)
        assertEquals("camera", requested)
        assertEquals("granted", (response.result as Map<*, *>)["state"])
    }

    @Test
    fun `data store is isolated by project id`() = runBlocking {
        val store = InMemoryRuntimeDataStore()
        val dispatcher = RuntimeBridgeDispatcher(store)
        dispatcher.dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "data.set", mapOf("key" to "theme", "value" to "dark")),
            session
        )
        val otherSession = session.copy(id = "session-456", projectId = "project-456")

        val stored = dispatcher.dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefi", "data.get", mapOf("key" to "theme")),
            session
        )
        val missing = dispatcher.dispatch(
            RuntimeBridgeRequest("2.0", "session-456", "wr_abcdefj", "data.get", mapOf("key" to "theme")),
            otherSession
        )

        assertEquals("dark", stored.result)
        assertNull(missing.result)
    }

    @Test
    fun `clearing one project data store keeps another project intact`() {
        val store = InMemoryRuntimeDataStore()
        store.set("project-a", "result", "passed")
        store.set("project-b", "result", "keep")

        store.clear("project-a")

        assertNull(store.get("project-a", "result"))
        assertEquals("keep", store.get("project-b", "result"))
    }

    @Test
    fun `data remove and clear are limited to the active project`() = runBlocking {
        val store = InMemoryRuntimeDataStore()
        val dispatcher = RuntimeBridgeDispatcher(store)
        val otherSession = session.copy(id = "session-456", projectId = "project-456")
        dispatcher.dispatch(RuntimeBridgeRequest("2.0", session.id, "wr_abcdefgh", "data.set", mapOf("key" to "first", "value" to "one")), session)
        dispatcher.dispatch(RuntimeBridgeRequest("2.0", session.id, "wr_abcdefi", "data.set", mapOf("key" to "second", "value" to "two")), session)
        dispatcher.dispatch(RuntimeBridgeRequest("2.0", otherSession.id, "wr_abcdefj", "data.set", mapOf("key" to "other", "value" to "keep")), otherSession)

        val removed = dispatcher.dispatch(RuntimeBridgeRequest("2.0", session.id, "wr_abcdefk", "data.remove", mapOf("key" to "first")), session)
        val cleared = dispatcher.dispatch(RuntimeBridgeRequest("2.0", session.id, "wr_abcdefl", "data.clear"), session)

        assertNull(removed.error)
        assertNull(cleared.error)
        assertNull(store.get(session.projectId, "first"))
        assertNull(store.get(session.projectId, "second"))
        assertEquals("keep", store.get(otherSession.projectId, "other"))
    }

    @Test
    fun `registered network handler receives the canonical network status method`() = runBlocking {
        val networkHandler = object : RuntimeCapabilityHandler {
            override val capabilityId = "network"

            override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? =
                mapOf("method" to method, "connected" to true)
        }
        val response = RuntimeBridgeDispatcher(InMemoryRuntimeDataStore(), listOf(networkHandler)).dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "network.status"),
            session.copy(selectedCapabilities = setOf("network"))
        )

        assertNull(response.error)
        assertEquals("network.status", (response.result as Map<*, *>) ["method"])
    }

    @Test
    fun `documented but unfinished handler method is reported as unsupported`() = runBlocking {
        val handler = object : RuntimeCapabilityHandler {
            override val capabilityId = "camera"
            override fun supports(method: String) = false
            override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = error("must not invoke")
        }

        val response = RuntimeBridgeDispatcher(InMemoryRuntimeDataStore(), listOf(handler)).dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "camera.recordVideo"),
            session
        )

        assertEquals("UNSUPPORTED", assertNotNull(response.error).code)
    }

    @Test
    fun `screen capture request is dispatched through the selected capability handler`() = runBlocking {
        val handler = object : RuntimeCapabilityHandler {
            override val capabilityId = "screen_capture"

            override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? =
                mapOf("method" to method)
        }
        val response = RuntimeBridgeDispatcher(InMemoryRuntimeDataStore(), listOf(handler)).dispatch(
            RuntimeBridgeRequest("2.0", "session-123", "wr_abcdefgh", "screenCapture.request"),
            session.copy(selectedCapabilities = setOf("screen_capture"))
        )

        assertNull(response.error)
        assertEquals("screenCapture.request", (response.result as Map<*, *>)["method"])
    }

    @Test
    fun `wrong runtime version is rejected before dispatch`() = runBlocking {
        val response = RuntimeBridgeDispatcher(InMemoryRuntimeDataStore()).dispatch(
            RuntimeBridgeRequest("1.0", "session-123", "wr_abcdefgh", "runtime.ready"),
            session
        )

        assertEquals("INVALID_ARGUMENT", assertNotNull(response.error).code)
    }
}
