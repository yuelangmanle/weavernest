package com.zhique.runtime.capability

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeBridgeEvent
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class MicrophoneCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId: String = "microphone"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf("microphone.requestPermission", "microphone.record")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "microphone.requestPermission" -> {
            permissionBroker.ensure(capabilityId)
            "granted"
        }

        "microphone.record" -> record(params, session)
        else -> throw IllegalArgumentException("Unsupported microphone method: $method")
    }

    private suspend fun record(params: Map<String, Any?>, session: RuntimeSession): Map<String, Any?> {
        permissionBroker.ensure(capabilityId)
        val duration = ((params["durationMs"] ?: params["duration"]) as? Number)?.toLong()?.coerceIn(250L, 15_000L) ?: 1_500L
        val output = File(appContext.cacheDir, "zhique/runtime/${session.projectId}/audio/${UUID.randomUUID()}.m4a").apply { parentFile?.mkdirs() }
        val recorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
        try {
            delay(duration)
            recorder.stop()
        } finally {
            recorder.release()
        }
        return mapOf(
            "uri" to Uri.fromFile(output).toString(),
            "mimeType" to "audio/mp4",
            "durationMs" to duration
        )
    }

    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
        MediaRecorder(appContext)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }
}

class SensorCapabilityHandler(
    context: Context,
    private val eventBus: RuntimeEventBus
) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId: String = "sensors"
    private val sensorManager = requireNotNull(context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
    private val subscriptions = mutableMapOf<String, SensorSubscription>()

    override fun supports(method: String) = method in setOf("sensor.subscribe", "sensor.unsubscribe")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "sensor.subscribe" -> subscribe(params, session)
        "sensor.unsubscribe" -> {
            unsubscribe(params["subscriptionId"] as? String ?: throw IllegalArgumentException("subscriptionId is required."))
            null
        }
        else -> throw IllegalArgumentException("Unsupported sensor method: $method")
    }

    override fun releaseSession(sessionId: String) {
        subscriptions.filterValues { it.sessionId == sessionId }.keys.toList().forEach(::unsubscribe)
    }

    private fun subscribe(params: Map<String, Any?>, session: RuntimeSession): Map<String, String> {
        require(params["type"] == "accelerometer") { "Only accelerometer is supported." }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: throw RuntimeCapabilityException(
            RuntimeBridgeError("UNSUPPORTED_DEVICE", "This device has no accelerometer.", capabilityId, recoverable = false)
        )
        val id = "sensor_${UUID.randomUUID()}"
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                eventBus.emit(
                    RuntimeBridgeEvent(
                        id,
                        mapOf(
                            "x" to event.values.getOrElse(0) { 0f },
                            "y" to event.values.getOrElse(1) { 0f },
                            "z" to event.values.getOrElse(2) { 0f },
                            "timestamp" to event.timestamp
                        )
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        subscriptions[id] = SensorSubscription(session.id, listener)
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        return mapOf("subscriptionId" to id)
    }

    private fun unsubscribe(id: String) {
        subscriptions.remove(id)?.let { sensorManager.unregisterListener(it.listener) }
    }

    private data class SensorSubscription(val sessionId: String, val listener: SensorEventListener)
}
