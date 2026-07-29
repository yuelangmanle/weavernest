package com.zhique.runtime.capability

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WifiInputPolicy {
    fun requireSsid(value: String): String = value.trim().also { ssid ->
        require(ssid.isNotBlank() && ssid.length <= 32) { "ssid must contain 1 to 32 characters." }
    }
}

class WifiScanCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId = "wifi_scan"
    private val appContext = context.applicationContext
    private val wifi = requireNotNull(appContext.getSystemService(WifiManager::class.java))

    override fun supports(method: String) = method in setOf("wifi.state", "wifi.scan")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "wifi.state" -> state()
        "wifi.scan" -> scan()
        else -> throw IllegalArgumentException("Unsupported Wi-Fi scan method: $method")
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // Android exposes scan results through this legacy-compatible API on API 29+.
    private suspend fun scan(): Map<String, Any?> {
        permissionBroker.ensure(capabilityId)
        if (!wifi.isWifiEnabled) throw specialFlow("Wi-Fi is turned off.", capabilityId)
        val requested = wifi.startScan()
        return mapOf(
            "requested" to requested,
            "networks" to wifi.scanResults.take(100).map { result ->
                mapOf("ssid" to result.SSID, "bssid" to result.BSSID, "level" to result.level, "frequency" to result.frequency)
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun state(): Map<String, Any?> = mapOf(
        "enabled" to wifi.isWifiEnabled,
        "ssid" to wifi.connectionInfo?.ssid?.takeUnless { it == "<unknown ssid>" }
    )
}

class WifiConnectionCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId = "wifi_connect"
    private val appContext = context.applicationContext
    private val connectivity = requireNotNull(appContext.getSystemService(ConnectivityManager::class.java))

    override fun supports(method: String) = method in setOf("wifi.requestConnection", "wifi.openSettings")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "wifi.requestConnection" -> requestConnection(params)
        "wifi.openSettings" -> openSettings()
        else -> throw IllegalArgumentException("Unsupported Wi-Fi connection method: $method")
    }

    private suspend fun requestConnection(params: Map<String, Any?>): Map<String, String> {
        permissionBroker.ensure(capabilityId)
        val ssid = WifiInputPolicy.requireSsid(params.requiredWifiString("ssid"))
        val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        (params["password"] as? String)?.takeIf(String::isNotBlank)?.let(builder::setWpa2Passphrase)
        val request = NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(builder.build()).build()
        return withTimeout(30_000) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (continuation.isActive) continuation.resume(mapOf("ssid" to ssid, "state" to "available"))
                    }

                    override fun onUnavailable() {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Android did not approve the Wi-Fi connection."))
                    }

                    override fun onLost(network: Network) = Unit
                }
                runCatching { connectivity.requestNetwork(request, callback) }
                    .onFailure { error -> if (continuation.isActive) continuation.resumeWithException(error) }
                continuation.invokeOnCancellation { runCatching { connectivity.unregisterNetworkCallback(callback) } }
            }
        }
    }

    private fun openSettings(): Map<String, Boolean> {
        appContext.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return mapOf("launched" to true)
    }
}

class LocalHotspotCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId = "local_hotspot"
    private val appContext = context.applicationContext
    private val wifi = requireNotNull(appContext.getSystemService(WifiManager::class.java))
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    override fun supports(method: String) = method in setOf(
        "hotspot.startLocalOnly",
        "hotspot.stop",
        "hotspot.state"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "hotspot.startLocalOnly" -> start()
        "hotspot.stop" -> {
            stop()
            null
        }
        "hotspot.state" -> mapOf("active" to (reservation != null))
        else -> throw IllegalArgumentException("Unsupported local hotspot method: $method")
    }

    override fun releaseSession(sessionId: String) {
        stop()
    }

    @SuppressLint("MissingPermission")
    private suspend fun start(): Map<String, Any?> {
        permissionBroker.ensure(capabilityId)
        if (reservation != null) return mapOf("active" to true)
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(next: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = next
                        @Suppress("DEPRECATION")
                        val ssid = next.wifiConfiguration?.SSID
                        if (continuation.isActive) continuation.resume(mapOf("active" to true, "ssid" to ssid))
                    }

                    override fun onFailed(reason: Int) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Android rejected the local hotspot request ($reason)."))
                    }
                }, Handler(Looper.getMainLooper()))
                continuation.invokeOnCancellation { stop() }
            }
        }
    }

    private fun stop() {
        reservation?.close()
        reservation = null
    }
}

private fun Map<String, Any?>.requiredWifiString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")

private fun specialFlow(message: String, capability: String) = RuntimeCapabilityException(
    RuntimeBridgeError("SPECIAL_FLOW_REQUIRED", message, capability, recoverable = true, action = "open_settings")
)
