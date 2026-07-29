package com.zhique.runtime.capability

import android.content.Context
import android.media.projection.MediaProjectionManager
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.capture.ScreenCaptureService
import com.zhique.runtime.permission.RuntimeUiHost

class ScreenCaptureCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost
) : RuntimeCapabilityHandler {
    override val capabilityId = "screen_capture"
    private val appContext = context.applicationContext
    private var authorization: Pair<Int, android.content.Intent>? = null

    override fun supports(method: String) = method in setOf(
        "screenCapture.request",
        "screenCapture.start",
        "screenCapture.stop"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "screenCapture.request" -> {
            val manager = appContext.getSystemService(MediaProjectionManager::class.java)
            val result = uiHost.requestScreenCapture(manager.createScreenCaptureIntent())
                ?: throw RuntimeCapabilityException(RuntimeBridgeError("USER_CANCELLED", "Screen capture permission was cancelled.", capabilityId, recoverable = true, action = "retry"))
            authorization = result.resultCode to result.resultData
            mapOf("granted" to true)
        }
        "screenCapture.start" -> {
            val (resultCode, resultData) = authorization ?: throw RuntimeCapabilityException(
                RuntimeBridgeError("SPECIAL_FLOW_REQUIRED", "Request screen capture permission before starting.", capabilityId, recoverable = true, action = "request_again")
            )
            ScreenCaptureService.start(appContext, resultCode, resultData)
            authorization = null
            mapOf("state" to "starting")
        }
        "screenCapture.stop" -> {
            ScreenCaptureService.stop(appContext)
            mapOf("state" to "stopped")
        }
        else -> throw IllegalArgumentException("Unsupported screen capture method: $method")
    }
}
