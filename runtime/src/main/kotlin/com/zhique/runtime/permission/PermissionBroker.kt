package com.zhique.runtime.permission

import android.Manifest
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException

class PermissionBroker(private val uiHost: RuntimeUiHost) {
    suspend fun ensure(capabilityId: String) {
        val permissions = runtimePermissions(capabilityId)
        if (permissions.isEmpty()) return
        val missing = permissions.filterTo(linkedSetOf()) { permission ->
            ContextCompat.checkSelfPermission(uiHost.activity, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return
        val results = uiHost.requestPermissions(missing)
        if (capabilityId == "geolocation" && hasAnyLocationPermission()) return
        if (missing.all { results[it] == true }) return
        val blocked = missing.any { permission ->
            results[permission] != true && !ActivityCompat.shouldShowRequestPermissionRationale(uiHost.activity, permission)
        }
        throw RuntimeCapabilityException(
            RuntimeBridgeError(
                code = if (blocked) "PERMISSION_BLOCKED" else "PERMISSION_DENIED",
                message = if (blocked) {
                    "Android requires this permission to be enabled in system settings."
                } else {
                    "The user denied the Android permission."
                },
                capability = capabilityId,
                recoverable = true,
                action = if (blocked) "open_settings" else "request_again"
            )
        )
    }

    private fun hasAnyLocationPermission(): Boolean = setOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).any { permission ->
        ContextCompat.checkSelfPermission(uiHost.activity, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun runtimePermissions(capabilityId: String): Set<String> = when (capabilityId) {
        "camera" -> setOf(Manifest.permission.CAMERA)
        "geolocation" -> setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        "notification" -> if (Build.VERSION.SDK_INT >= 33) setOf(Manifest.permission.POST_NOTIFICATIONS) else emptySet()
        "contacts" -> setOf(Manifest.permission.READ_CONTACTS)
        "microphone" -> setOf(Manifest.permission.RECORD_AUDIO)
        "bluetooth_le" -> if (Build.VERSION.SDK_INT >= 31) {
            setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        "bluetooth_classic" -> if (Build.VERSION.SDK_INT >= 31) {
            setOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptySet()
        }
        "wifi_scan" -> if (Build.VERSION.SDK_INT >= 33) {
            setOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        "wifi_connect", "local_hotspot" -> if (Build.VERSION.SDK_INT >= 33) {
            setOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        "calendar" -> setOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        "speech" -> setOf(Manifest.permission.RECORD_AUDIO)
        else -> emptySet()
    }
}
