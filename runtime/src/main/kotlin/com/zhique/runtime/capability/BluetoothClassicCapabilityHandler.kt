package com.zhique.runtime.capability

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker

/** Exposes only paired-device discovery and the Android settings flow, never raw classic sockets. */
class BluetoothClassicCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId = "bluetooth_classic"
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    override fun supports(method: String) = method in setOf(
        "bluetooth.classic.listPaired",
        "bluetooth.classic.openSettings"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "bluetooth.classic.listPaired" -> listPaired()
        "bluetooth.classic.openSettings" -> openSettings()
        else -> throw IllegalArgumentException("Unsupported classic Bluetooth method: $method")
    }

    @SuppressLint("MissingPermission")
    private suspend fun listPaired(): Map<String, Any?> {
        permissionBroker.ensure(capabilityId)
        val currentAdapter = requireAdapter()
        if (!currentAdapter.isEnabled) throw RuntimeCapabilityException(
            RuntimeBridgeError("SPECIAL_FLOW_REQUIRED", "Bluetooth is turned off.", capabilityId, recoverable = true, action = "open_settings")
        )
        return mapOf(
            "devices" to currentAdapter.bondedDevices.orEmpty().sortedBy { device -> device.address }.map { device ->
                mapOf(
                    "id" to device.address,
                    "name" to device.name,
                    "bondState" to device.bondState,
                    "type" to device.type
                )
            }
        )
    }

    private fun openSettings(): Map<String, Boolean> {
        appContext.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return mapOf("launched" to true)
    }

    private fun requireAdapter(): BluetoothAdapter = adapter ?: throw RuntimeCapabilityException(
        RuntimeBridgeError("UNSUPPORTED_DEVICE", "This device has no Bluetooth adapter.", capabilityId, recoverable = false)
    )
}
