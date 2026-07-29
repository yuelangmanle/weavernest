package com.zhique.runtime.capability

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume

object UsbDeviceIdPolicy {
    fun requireId(value: String): String = value.trim().also { id ->
        require(id.isNotBlank() && id.length <= 256) { "deviceId must be a nonblank USB device name." }
    }
}

class UsbCapabilityHandler(context: Context) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId = "usb"
    private val appContext = context.applicationContext
    private val manager = requireNotNull(appContext.getSystemService(UsbManager::class.java))
    private val connections = mutableMapOf<String, UsbDeviceConnection>()

    override fun supports(method: String) = method in setOf(
        "usb.list",
        "usb.requestPermission",
        "usb.open",
        "usb.close"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "usb.list" -> mapOf("devices" to manager.deviceList.values.map(::deviceInfo))
        "usb.requestPermission" -> requestPermission(params.requiredUsbId("deviceId"))
        "usb.open" -> open(params.requiredUsbId("deviceId"))
        "usb.close" -> {
            close(params.requiredUsbId("deviceId"))
            null
        }
        else -> throw IllegalArgumentException("Unsupported USB method: $method")
    }

    override fun releaseSession(sessionId: String) {
        connections.values.forEach(UsbDeviceConnection::close)
        connections.clear()
    }

    private suspend fun requestPermission(id: String): Map<String, Any?> {
        val device = device(id)
        if (manager.hasPermission(device)) return mapOf("deviceId" to id, "granted" to true)
        val action = "${appContext.packageName}.USB_PERMISSION.${UUID.randomUUID()}"
        return withTimeout(30_000) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action != action) return
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        runCatching { appContext.unregisterReceiver(this) }
                        if (continuation.isActive) continuation.resume(mapOf("deviceId" to id, "granted" to granted))
                    }
                }
                ContextCompat.registerReceiver(appContext, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val request = PendingIntent.getBroadcast(appContext, 0, Intent(action).setPackage(appContext.packageName), flags)
                manager.requestPermission(device, request)
                continuation.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }
            }
        }
    }

    private fun open(id: String): Map<String, Any?> {
        val device = device(id)
        if (!manager.hasPermission(device)) throw RuntimeCapabilityException(
            RuntimeBridgeError("PERMISSION_DENIED", "Android USB permission has not been granted for this device.", capabilityId, recoverable = true, action = "request_again")
        )
        connections.remove(id)?.close()
        val connection = manager.openDevice(device) ?: throw IllegalStateException("Android could not open this USB device.")
        connections[id] = connection
        return deviceInfo(device) + mapOf("opened" to true)
    }

    private fun close(id: String) {
        connections.remove(id)?.close()
    }

    private fun device(id: String): UsbDevice = manager.deviceList[UsbDeviceIdPolicy.requireId(id)]
        ?: throw IllegalArgumentException("The requested USB device is no longer connected.")

    private fun deviceInfo(device: UsbDevice): Map<String, Any?> = mapOf(
        "deviceId" to device.deviceName,
        "vendorId" to device.vendorId,
        "productId" to device.productId,
        "deviceClass" to device.deviceClass,
        "interfaces" to device.interfaceCount
    )
}

private fun Map<String, Any?>.requiredUsbId(key: String): String = UsbDeviceIdPolicy.requireId(
    this[key] as? String ?: throw IllegalArgumentException("$key is required.")
)
