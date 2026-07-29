package com.zhique.runtime.bridge

import com.zhique.core.project.CapabilityRegistry
import com.zhique.core.project.RuntimeApiCatalog

data class RuntimeSession(
    val id: String,
    val projectId: String,
    val selectedCapabilities: Set<String>,
    val runtimeName: String,
    val androidApi: Int
)

data class RuntimeBridgeRequest(
    val protocolVersion: String,
    val sessionId: String,
    val requestId: String,
    val method: String,
    val params: Map<String, Any?> = emptyMap()
)

data class RuntimeBridgeError(
    val code: String,
    val message: String,
    val capability: String? = null,
    val recoverable: Boolean = false,
    val action: String? = null
)

data class RuntimeBridgeResponse(
    val requestId: String,
    val result: Any? = null,
    val error: RuntimeBridgeError? = null
)

interface RuntimeDataStore {
    fun get(projectId: String, key: String): String?
    fun set(projectId: String, key: String, value: String)
    fun remove(projectId: String, key: String)
    fun clear(projectId: String)
}

class InMemoryRuntimeDataStore : RuntimeDataStore {
    private val values = mutableMapOf<String, MutableMap<String, String>>()

    override fun get(projectId: String, key: String): String? = values[projectId]?.get(key)

    override fun set(projectId: String, key: String, value: String) {
        values.getOrPut(projectId) { mutableMapOf() }[key] = value
    }

    override fun remove(projectId: String, key: String) {
        values[projectId]?.remove(key)
    }

    override fun clear(projectId: String) {
        values.remove(projectId)
    }
}

interface RuntimeCapabilityHandler {
    val capabilityId: String

    fun supports(method: String): Boolean = true

    suspend fun invoke(
        method: String,
        params: Map<String, Any?>,
        session: RuntimeSession
    ): Any?
}

/** Controls the generic capability request/settings APIs without giving web code an Activity. */
interface RuntimeCapabilityControls {
    suspend fun request(capabilityId: String, session: RuntimeSession): Any?

    suspend fun openSettings(capabilityId: String, session: RuntimeSession): Any?
}

/**
 * Routes validated Runtime 2.0 calls. WebView-origin validation belongs to WebRuntimeHost;
 * this class remains platform-neutral so capability policy can be unit-tested without a device.
 */
class RuntimeBridgeDispatcher(
    private val dataStore: RuntimeDataStore,
    handlers: Collection<RuntimeCapabilityHandler> = emptyList(),
    private val capabilityControls: RuntimeCapabilityControls? = null
) {
    private val handlersByCapability = handlers.associateBy(RuntimeCapabilityHandler::capabilityId)

    suspend fun dispatch(request: RuntimeBridgeRequest, session: RuntimeSession): RuntimeBridgeResponse {
        validateEnvelope(request, session)?.let { error -> return failure(request.requestId, error) }

        return when (request.method) {
            "runtime.ready" -> success(
                request.requestId,
                mapOf(
                    "runtime" to session.runtimeName,
                    "apiVersion" to RuntimeApiCatalog.apiVersion,
                    "androidApi" to session.androidApi,
                    "projectId" to session.projectId,
                    "selectedCapabilities" to session.selectedCapabilities.sorted()
                )
            )

            "capabilities.list" -> success(
                request.requestId,
                CapabilityRegistry.all().map { definition -> capabilityState(definition.id, session) }
            )

            "capabilities.status" -> {
                val capabilityId = request.params.string("id")
                    ?: return failure(request.requestId, invalidArgument("capabilities.status requires id."))
                val canonicalId = CapabilityRegistry.canonicalId(capabilityId)
                    ?: return failure(request.requestId, invalidArgument("Unknown capability: $capabilityId"))
                success(request.requestId, capabilityState(canonicalId, session))
            }

            "capabilities.request" -> requestCapability(request, session)

            "capabilities.openSettings" -> openCapabilitySettings(request, session)

            "data.get" -> {
                val key = request.params.string("key")
                    ?: return failure(request.requestId, invalidArgument("data.get requires key."))
                success(request.requestId, dataStore.get(session.projectId, key))
            }

            "data.set" -> {
                val key = request.params.string("key")
                    ?: return failure(request.requestId, invalidArgument("data.set requires key."))
                val value = request.params.string("value")
                    ?: return failure(request.requestId, invalidArgument("data.set requires value."))
                dataStore.set(session.projectId, key, value)
                success(request.requestId, null)
            }

            "data.remove" -> {
                val key = request.params.string("key")
                    ?: return failure(request.requestId, invalidArgument("data.remove requires key."))
                dataStore.remove(session.projectId, key)
                success(request.requestId, null)
            }

            "data.clear" -> {
                dataStore.clear(session.projectId)
                success(request.requestId, null)
            }

            else -> dispatchCapability(request, session)
        }
    }

    fun releaseSession(sessionId: String) {
        handlersByCapability.values.filterIsInstance<RuntimeLifecycleHandler>().forEach { handler ->
            handler.releaseSession(sessionId)
        }
    }

    private suspend fun dispatchCapability(
        request: RuntimeBridgeRequest,
        session: RuntimeSession
    ): RuntimeBridgeResponse {
        val capabilityId = RuntimeApiCatalog.capabilityForBridgeMethod(request.method)
            ?: if (request.method == "sensor.unsubscribe") "sensors" else null
        if (capabilityId == null) {
            val message = if (RuntimeApiCatalog.containsBridgeMethod(request.method)) {
                "The Runtime API method ${request.method} is documented but not implemented in this Runtime build."
            } else {
                "The Runtime API method ${request.method} is not registered."
            }
            return failure(
                request.requestId,
                RuntimeBridgeError(code = "UNSUPPORTED", message = message, recoverable = false)
            )
        }
        if (capabilityId !in canonicalCapabilities(session)) {
            return failure(
                request.requestId,
                RuntimeBridgeError(
                    code = "CAPABILITY_NOT_SELECTED",
                    message = "The project did not select $capabilityId.",
                    capability = capabilityId,
                    recoverable = true,
                    action = "select_capability"
                )
            )
        }
        val handler = handlersByCapability[capabilityId]
            ?: return failure(
                request.requestId,
                RuntimeBridgeError(
                    code = "UNSUPPORTED",
                    message = "$capabilityId is not implemented in this Runtime build.",
                    capability = capabilityId,
                    recoverable = false
                )
            )
        if (!handler.supports(request.method)) {
            return failure(
                request.requestId,
                RuntimeBridgeError(
                    code = "UNSUPPORTED",
                    message = "${request.method} is not implemented in this Runtime build.",
                    capability = capabilityId,
                    recoverable = false
                )
            )
        }
        return try {
            success(request.requestId, handler.invoke(request.method, request.params, session))
        } catch (error: RuntimeCapabilityException) {
            failure(request.requestId, error.runtimeError)
        } catch (error: IllegalArgumentException) {
            failure(request.requestId, invalidArgument(error.message ?: "Invalid parameters."))
        } catch (error: Exception) {
            failure(
                request.requestId,
                RuntimeBridgeError(
                    code = "NATIVE_FAILURE",
                    message = "The native operation could not be completed.",
                    capability = capabilityId,
                    recoverable = true,
                    action = "retry"
                )
            )
        }
    }

    private suspend fun requestCapability(request: RuntimeBridgeRequest, session: RuntimeSession): RuntimeBridgeResponse {
        val capabilityId = request.params.string("id")
            ?: return failure(request.requestId, invalidArgument("capabilities.request requires id."))
        val canonicalId = CapabilityRegistry.canonicalId(capabilityId)
            ?: return failure(request.requestId, invalidArgument("Unknown capability: $capabilityId"))
        if (canonicalId !in canonicalCapabilities(session)) return notSelected(request.requestId, canonicalId)
        val controls = capabilityControls ?: return unsupportedControls(request.requestId, canonicalId)
        return try {
            success(request.requestId, controls.request(canonicalId, session))
        } catch (error: RuntimeCapabilityException) {
            failure(request.requestId, error.runtimeError)
        } catch (error: IllegalArgumentException) {
            failure(request.requestId, invalidArgument(error.message ?: "Invalid capability request."))
        } catch (_: Exception) {
            failure(request.requestId, RuntimeBridgeError("NATIVE_FAILURE", "Android could not request this capability.", canonicalId, recoverable = true, action = "retry"))
        }
    }

    private suspend fun openCapabilitySettings(request: RuntimeBridgeRequest, session: RuntimeSession): RuntimeBridgeResponse {
        val capabilityId = request.params.string("id")
            ?: return failure(request.requestId, invalidArgument("capabilities.openSettings requires id."))
        val canonicalId = CapabilityRegistry.canonicalId(capabilityId)
            ?: return failure(request.requestId, invalidArgument("Unknown capability: $capabilityId"))
        if (canonicalId !in canonicalCapabilities(session)) return notSelected(request.requestId, canonicalId)
        val controls = capabilityControls ?: return unsupportedControls(request.requestId, canonicalId)
        return try {
            success(request.requestId, controls.openSettings(canonicalId, session))
        } catch (error: RuntimeCapabilityException) {
            failure(request.requestId, error.runtimeError)
        } catch (_: Exception) {
            failure(request.requestId, RuntimeBridgeError("NATIVE_FAILURE", "Android could not open this capability's settings.", canonicalId, recoverable = true, action = "retry"))
        }
    }

    private fun validateEnvelope(request: RuntimeBridgeRequest, session: RuntimeSession): RuntimeBridgeError? = when {
        request.protocolVersion != RuntimeApiCatalog.apiVersion -> invalidArgument("Unsupported Runtime protocol version.")
        request.sessionId != session.id -> RuntimeBridgeError(
            code = "RUNTIME_NOT_READY",
            message = "The preview session has expired.",
            recoverable = true,
            action = "reload"
        )
        !request.requestId.matches(requestIdPattern) -> invalidArgument("Invalid request id.")
        request.method.length !in 1..96 -> invalidArgument("Invalid Runtime method.")
        else -> null
    }

    private fun capabilityState(capabilityId: String, session: RuntimeSession): Map<String, Any?> {
        val selected = capabilityId in canonicalCapabilities(session)
        val state = when {
            !selected -> "not_selected"
            handlersByCapability.containsKey(capabilityId) -> "not_requested"
            else -> "not_implemented"
        }
        return mapOf("id" to capabilityId, "selected" to selected, "state" to state)
    }

    private fun notSelected(requestId: String, capabilityId: String) = failure(
        requestId,
        RuntimeBridgeError(
            code = "CAPABILITY_NOT_SELECTED",
            message = "The project did not select $capabilityId.",
            capability = capabilityId,
            recoverable = true,
            action = "select_capability"
        )
    )

    private fun unsupportedControls(requestId: String, capabilityId: String) = failure(
        requestId,
        RuntimeBridgeError(
            code = "UNSUPPORTED",
            message = "This Runtime host cannot request or open settings for $capabilityId.",
            capability = capabilityId,
            recoverable = false
        )
    )

    private fun canonicalCapabilities(session: RuntimeSession): Set<String> = session.selectedCapabilities.mapNotNullTo(linkedSetOf()) {
        CapabilityRegistry.canonicalId(it)
    }

    private fun success(requestId: String, result: Any?): RuntimeBridgeResponse = RuntimeBridgeResponse(requestId, result)

    private fun failure(requestId: String, error: RuntimeBridgeError): RuntimeBridgeResponse = RuntimeBridgeResponse(requestId, error = error)

    private fun invalidArgument(message: String): RuntimeBridgeError = RuntimeBridgeError(
        code = "INVALID_ARGUMENT",
        message = message,
        recoverable = false
    )

    private companion object {
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,80}$")
    }
}

class RuntimeCapabilityException(val runtimeError: RuntimeBridgeError) : RuntimeException(runtimeError.message)

private fun Map<String, Any?>.string(key: String): String? = this[key] as? String
