package com.zhique.runtime.capability

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeBridgeEvent
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import com.zhique.runtime.permission.RuntimeUiHost
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import java.util.UUID

class CameraCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId: String = "camera"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method == "camera.capture"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "camera.capture") { "Unsupported camera method: $method" }
        permissionBroker.ensure(capabilityId)
        val uri = createDestination()
        if (!uiHost.takePicture(uri)) {
            appContext.contentResolver.delete(uri, null, null)
            throw RuntimeCapabilityException(
                RuntimeBridgeError("USER_CANCELLED", "The camera capture was cancelled.", capabilityId, recoverable = true, action = "retry")
            )
        }
        val dimensions = appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.Options().also { BitmapFactory.decodeStream(input, null, it) }
        }
        return buildMap<String, Any?> {
            put("uri", uri.toString())
            put("mimeType", "image/jpeg")
            dimensions?.outWidth?.takeIf { it > 0 }?.let { put("width", it) }
            dimensions?.outHeight?.takeIf { it > 0 }?.let { put("height", it) }
        }
    }

    private fun createDestination(): Uri = requireNotNull(
        appContext.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "zhique_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Zhique")
            }
        )
    ) { "Android could not create a photo destination." }
}

class GeolocationCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker,
    private val eventBus: RuntimeEventBus
) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId: String = "geolocation"
    private val appContext = context.applicationContext
    private val locationManager = requireNotNull(appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
    private val watches = mutableMapOf<String, LocationWatch>()

    override fun supports(method: String) = method in setOf("geolocation.getCurrentPosition", "geolocation.watchPosition", "geolocation.clearWatch")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        return when (method) {
            "geolocation.getCurrentPosition" -> {
                permissionBroker.ensure(capabilityId)
                val timeoutMs = ((params["timeoutMs"] as? Number)?.toLong() ?: 15_000L).coerceIn(1_000L, 60_000L)
                val accuracy = params["accuracy"] as? String ?: "balanced"
                withTimeout(timeoutMs) { requestLocation(selectProvider(accuracy)) }.toRuntimePosition()
            }
            "geolocation.watchPosition" -> watch(params, session)
            "geolocation.clearWatch" -> {
                clearWatch((params["subscriptionId"] ?: params["id"]) as? String ?: throw IllegalArgumentException("subscriptionId is required."))
                null
            }
            else -> throw IllegalArgumentException("Unsupported geolocation method: $method")
        }
    }

    override fun releaseSession(sessionId: String) {
        watches.filterValues { watch -> watch.sessionId == sessionId }.keys.toList().forEach(::clearWatch)
    }

    private fun selectProvider(accuracy: String): String {
        val candidates = if (accuracy == "high") {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        }
        return candidates.firstOrNull(locationManager::isProviderEnabled) ?: throw RuntimeCapabilityException(
            RuntimeBridgeError("UNSUPPORTED_DEVICE", "Location is disabled on this device.", capabilityId, recoverable = true, action = "open_settings")
        )
    }

    @Suppress("MissingPermission")
    private suspend fun requestLocation(provider: String): Location = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(location)
            }

            override fun onProviderDisabled(provider: String) {
                locationManager.removeUpdates(this)
                if (continuation.isActive) continuation.cancel()
            }
        }
        continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
    }

    @Suppress("MissingPermission")
    private suspend fun watch(params: Map<String, Any?>, session: RuntimeSession): Map<String, String> {
        permissionBroker.ensure(capabilityId)
        val provider = selectProvider(params["accuracy"] as? String ?: "balanced")
        val id = "location_${UUID.randomUUID()}"
        val interval = ((params["intervalMs"] as? Number)?.toLong() ?: 2_000L).coerceIn(1_000L, 60_000L)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                eventBus.emit(RuntimeBridgeEvent(id, location.toRuntimePosition()))
            }

            override fun onProviderDisabled(provider: String) {
                eventBus.emit(RuntimeBridgeEvent(id, mapOf("error" to "LOCATION_DISABLED")))
            }
        }
        watches[id] = LocationWatch(session.id, listener)
        locationManager.requestLocationUpdates(provider, interval, 0f, listener, Looper.getMainLooper())
        return mapOf("subscriptionId" to id)
    }

    private fun clearWatch(id: String) {
        watches.remove(id)?.let { watch -> locationManager.removeUpdates(watch.listener) }
    }

    private data class LocationWatch(val sessionId: String, val listener: LocationListener)
}

class ContactsCapabilityHandler(
    context: Context,
    private val uiHost: RuntimeUiHost,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId: String = "contacts"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method == "contacts.pick"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "contacts.pick") { "Unsupported contacts method: $method" }
        permissionBroker.ensure(capabilityId)
        val contactUri = uiHost.pickContact() ?: throw RuntimeCapabilityException(
            RuntimeBridgeError("USER_CANCELLED", "No contact was selected.", capabilityId, recoverable = true, action = "retry")
        )
        return readContact(contactUri)
    }

    private fun readContact(uri: Uri): Map<String, String?> {
        val resolver = appContext.contentResolver
        val contact = resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)) to
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
        } ?: throw RuntimeCapabilityException(
            RuntimeBridgeError("NATIVE_FAILURE", "Android could not read the selected contact.", capabilityId, recoverable = true, action = "retry")
        )
        val phone = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contact.first),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) else null
        }
        return mapOf("name" to contact.second, "phone" to phone)
    }
}

private fun Location.toRuntimePosition(): Map<String, Any?> = mapOf(
    "latitude" to latitude,
    "longitude" to longitude,
    "accuracyMeters" to accuracy,
    "timestamp" to time
)
