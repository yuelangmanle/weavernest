package com.zhique.runtime.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityControls
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeSession

/** Android implementation for `weaver.capabilities.request()` and `openSettings()`. */
class AndroidRuntimeCapabilityControls(
    context: Context,
    uiHost: RuntimeUiHost
) : RuntimeCapabilityControls {
    private val appContext = context.applicationContext
    private val permissionBroker = PermissionBroker(uiHost)

    override suspend fun request(capabilityId: String, session: RuntimeSession): Any? {
        when (capabilityId) {
            "background_location", "manage_external_storage", "screen_capture", "background_tasks" -> throw specialFlow(capabilityId)
            else -> permissionBroker.ensure(capabilityId)
        }
        return mapOf("id" to capabilityId, "state" to "granted_or_not_required")
    }

    override suspend fun openSettings(capabilityId: String, session: RuntimeSession): Any? {
        val intent = when (capabilityId) {
            "manage_external_storage" -> Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri())
            "wifi_scan", "wifi_connect", "local_hotspot" -> Intent(Settings.Panel.ACTION_WIFI)
            "bluetooth_le", "bluetooth_classic" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return mapOf("id" to capabilityId, "state" to "settings_opened")
    }

    private fun packageUri() = Uri.parse("package:${appContext.packageName}")

    private fun specialFlow(capabilityId: String) = RuntimeCapabilityException(
        RuntimeBridgeError(
            code = "SPECIAL_FLOW_REQUIRED",
            message = "Android requires a dedicated system confirmation or settings flow for $capabilityId.",
            capability = capabilityId,
            recoverable = true,
            action = "open_settings"
        )
    )
}
