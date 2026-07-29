package com.zhique.runtime.capability

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeBridgeEvent
import com.zhique.runtime.bridge.RuntimeCapabilityException
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.bridge.RuntimeLifecycleHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

object BluetoothAddressPolicy {
    private val address = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    fun requireAddress(value: String): String {
        require(address.matches(value)) { "id must be a Bluetooth hardware address." }
        return value.uppercase()
    }
}

data class BluetoothCharacteristicRequest(
    val address: String,
    val serviceUuid: UUID,
    val characteristicUuid: UUID
) {
    companion object {
        fun from(params: Map<String, Any?>): BluetoothCharacteristicRequest {
            val values = params.requestValues()
            return BluetoothCharacteristicRequest(
                address = BluetoothAddressPolicy.requireAddress(values.requiredString("id", "address")),
                serviceUuid = values.requiredUuid("serviceUuid", "service"),
                characteristicUuid = values.requiredUuid("characteristicUuid", "characteristic")
            )
        }
    }
}

object BluetoothValuePolicy {
    private const val MAX_VALUE_BYTES = 512

    fun decode(params: Map<String, Any?>): ByteArray {
        val values = params.requestValues()
        val encoded = values["valueBase64"] as? String
        val text = values["text"] as? String
        require((encoded == null) xor (text == null)) { "Provide exactly one of valueBase64 or text." }
        val bytes = encoded?.let {
            runCatching { Base64.getDecoder().decode(it) }
                .getOrElse { throw IllegalArgumentException("valueBase64 is not valid Base64.") }
        } ?: text!!.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_VALUE_BYTES) { "BLE values are limited to 512 bytes." }
        return bytes
    }

    fun encode(value: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(value)
}

class BluetoothLeCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker,
    private val eventBus: RuntimeEventBus
) : RuntimeCapabilityHandler, RuntimeLifecycleHandler {
    override val capabilityId = "bluetooth_le"

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val scans = ConcurrentHashMap<String, ScanSubscription>()
    private val connections = ConcurrentHashMap<String, ManagedConnection>()
    private val notifications = ConcurrentHashMap<String, NotificationSubscription>()

    override fun supports(method: String) = method in setOf(
        "bluetooth.scan", "bluetooth.stopScan", "bluetooth.connect", "bluetooth.disconnect",
        "bluetooth.discover", "bluetooth.read", "bluetooth.write", "bluetooth.subscribe",
        "bluetooth.unsubscribe"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "bluetooth.scan" -> scan(params, session)
        "bluetooth.stopScan" -> {
            stopScan(params["subscriptionId"] as? String, session.id)
            null
        }
        "bluetooth.connect" -> connect(BluetoothAddressPolicy.requireAddress(params.requiredString("id")), session)
        "bluetooth.disconnect" -> {
            disconnect(BluetoothAddressPolicy.requireAddress(params.requiredString("id")), session.id)
            null
        }
        "bluetooth.discover" -> discover(BluetoothAddressPolicy.requireAddress(params.requiredString("id")), session.id)
        "bluetooth.read" -> read(BluetoothCharacteristicRequest.from(params), session.id)
        "bluetooth.write" -> write(BluetoothCharacteristicRequest.from(params), BluetoothValuePolicy.decode(params), params, session.id)
        "bluetooth.subscribe" -> subscribe(BluetoothCharacteristicRequest.from(params), session)
        "bluetooth.unsubscribe" -> {
            unsubscribe(params.requiredString("subscriptionId"), session.id)
            null
        }
        else -> throw IllegalArgumentException("Unsupported Bluetooth method: " + method)
    }

    override fun releaseSession(sessionId: String) {
        scans.entries.filter { it.value.sessionId == sessionId }.map { it.key }.forEach { stopScan(it, sessionId) }
        notifications.entries.filter { it.value.sessionId == sessionId }.map { it.key }.forEach { notifications.remove(it) }
        connections.entries.filter { it.value.sessionId == sessionId }.forEach { closeConnection(it.key, it.value) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scan(params: Map<String, Any?>, session: RuntimeSession): Map<String, String> {
        permissionBroker.ensure(capabilityId)
        val scanner = scanner()
        val id = "ble_scan_" + UUID.randomUUID()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                eventBus.emit(RuntimeBridgeEvent(id, mapOf(
                    "type" to "device",
                    "id" to result.device.address,
                    "name" to result.device.name,
                    "rssi" to result.rssi,
                    "timestamp" to result.timestampNanos
                )))
            }

            override fun onScanFailed(errorCode: Int) {
                eventBus.emit(RuntimeBridgeEvent(id, mapOf("type" to "error", "code" to "SCAN_FAILED", "androidCode" to errorCode)))
            }
        }
        val filters = params.requestValues()["serviceUuids"].asUuidList().map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        scans[id] = ScanSubscription(session.id, scanner, callback)
        try {
            scanner.startScan(filters.ifEmpty { null }, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (error: SecurityException) {
            scans.remove(id)
            throw permissionDenied()
        }
        return mapOf("subscriptionId" to id, "state" to "scanning")
    }

    @SuppressLint("MissingPermission")
    private fun stopScan(id: String?, sessionId: String) {
        scans.entries
            .filter { (id == null || it.key == id) && it.value.sessionId == sessionId }
            .forEach { entry -> scans.remove(entry.key)?.let { runCatching { it.scanner.stopScan(it.callback) } } }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connect(address: String, session: RuntimeSession): Map<String, String> {
        permissionBroker.ensure(capabilityId)
        val bluetooth = requireAdapter()
        if (!bluetooth.isEnabled) throw settingsRequired()
        connections.remove(address)?.let { closeConnection(address, it) }
        val connection = ManagedConnection(address, session.id, eventBus) { characteristicUuid ->
            notifications.values
                .filter { it.address == address && it.characteristicUuid == characteristicUuid }
                .map { it.id }
        }
        val gatt = bluetooth.getRemoteDevice(address).connectGatt(appContext, false, connection.callback)
            ?: throw failure("Android could not start the BLE connection.")
        connection.gatt = gatt
        connections[address] = connection
        try {
            withTimeout(CONNECTION_TIMEOUT_MS) { connection.connected.await() }
        } catch (_: TimeoutCancellationException) {
            closeConnection(address, connection)
            throw timeout("The BLE device did not connect in time.")
        } catch (error: RuntimeCapabilityException) {
            closeConnection(address, connection)
            throw error
        }
        return mapOf("id" to address, "state" to "connected")
    }

    @SuppressLint("MissingPermission")
    private fun disconnect(address: String, sessionId: String) {
        val connection = requireConnection(address, sessionId)
        closeConnection(address, connection)
    }

    @SuppressLint("MissingPermission")
    private suspend fun discover(address: String, sessionId: String): Map<String, Any?> {
        val connection = requireConnection(address, sessionId)
        val services = connection.operations.withLock {
            connection.services?.let { return@withLock it }
            val pending = CompletableDeferred<List<Map<String, Any?>>>()
            connection.discovery = pending
            if (!gatt(connection).discoverServices()) {
                connection.discovery = null
                throw failure("Android could not start BLE service discovery.")
            }
            try {
                withTimeout(OPERATION_TIMEOUT_MS) { pending.await() }
            } catch (_: TimeoutCancellationException) {
                throw timeout("BLE service discovery timed out.")
            } finally {
                connection.discovery = null
            }
        }
        connection.services = services
        return mapOf("id" to address, "services" to services)
    }

    @SuppressLint("MissingPermission")
    private suspend fun read(request: BluetoothCharacteristicRequest, sessionId: String): Map<String, Any?> {
        val connection = requireConnection(request.address, sessionId)
        return connection.operations.withLock {
            val characteristic = characteristic(connection, request)
            require(characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                "The BLE characteristic does not support read."
            }
            val pending = CompletableDeferred<ByteArray>()
            connection.read = PendingRead(request.characteristicUuid, pending)
            if (!gatt(connection).readCharacteristic(characteristic)) {
                connection.read = null
                throw failure("Android could not start the BLE read.")
            }
            try {
                val value = withTimeout(OPERATION_TIMEOUT_MS) { pending.await() }
                mapOf(
                    "id" to request.address,
                    "serviceUuid" to request.serviceUuid.toString(),
                    "characteristicUuid" to request.characteristicUuid.toString(),
                    "valueBase64" to BluetoothValuePolicy.encode(value)
                )
            } catch (_: TimeoutCancellationException) {
                throw timeout("BLE characteristic read timed out.")
            } finally {
                connection.read = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun write(
        request: BluetoothCharacteristicRequest,
        value: ByteArray,
        params: Map<String, Any?>,
        sessionId: String
    ): Map<String, Any?> {
        val connection = requireConnection(request.address, sessionId)
        return connection.operations.withLock {
            val characteristic = characteristic(connection, request)
            val withoutResponse = (params.requestValues()["writeType"] as? String)?.lowercase() == "withoutresponse"
            val supported = if (withoutResponse) {
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            } else {
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            }
            require(supported) { "The BLE characteristic does not support the requested write mode." }
            characteristic.writeType = if (withoutResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.value = value
            val pending = if (withoutResponse) null else CompletableDeferred<Unit>().also {
                connection.write = PendingWrite(request.characteristicUuid, it)
            }
            if (!gatt(connection).writeCharacteristic(characteristic)) {
                connection.write = null
                throw failure("Android could not start the BLE write.")
            }
            try {
                pending?.let { withTimeout(OPERATION_TIMEOUT_MS) { it.await() } }
            } catch (_: TimeoutCancellationException) {
                throw timeout("BLE characteristic write timed out.")
            } finally {
                connection.write = null
            }
            mapOf("id" to request.address, "bytes" to value.size, "writeType" to if (withoutResponse) "withoutResponse" else "withResponse")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun subscribe(request: BluetoothCharacteristicRequest, session: RuntimeSession): Map<String, String> {
        val connection = requireConnection(request.address, session.id)
        val characteristic = characteristic(connection, request)
        val notify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val indicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        require(notify || indicate) { "The BLE characteristic does not support notifications or indications." }
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIGURATION_UUID)
            ?: throw failure("The BLE characteristic has no client configuration descriptor.")
        val id = "ble_notify_" + UUID.randomUUID()
        connection.operations.withLock {
            val pending = CompletableDeferred<Unit>()
            connection.descriptor = PendingDescriptor(descriptor.uuid, pending)
            require(gatt(connection).setCharacteristicNotification(characteristic, true)) {
                "Android could not enable BLE notifications."
            }
            descriptor.value = if (notify) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            if (!gatt(connection).writeDescriptor(descriptor)) {
                connection.descriptor = null
                throw failure("Android could not enable the BLE notification descriptor.")
            }
            try {
                withTimeout(OPERATION_TIMEOUT_MS) { pending.await() }
            } catch (_: TimeoutCancellationException) {
                throw timeout("BLE notification setup timed out.")
            } finally {
                connection.descriptor = null
            }
        }
        notifications[id] = NotificationSubscription(id, session.id, request.address, request.serviceUuid, request.characteristicUuid)
        return mapOf("id" to id, "subscriptionId" to id, "state" to "subscribed")
    }

    @SuppressLint("MissingPermission")
    private fun unsubscribe(id: String, sessionId: String) {
        val subscription = notifications[id] ?: return
        require(subscription.sessionId == sessionId) { "This BLE subscription belongs to another runtime session." }
        notifications.remove(id)
        val connection = connections[subscription.address] ?: return
        val characteristic = connection.gatt?.getService(subscription.serviceUuid)?.getCharacteristic(subscription.characteristicUuid) ?: return
        runCatching { connection.gatt?.setCharacteristicNotification(characteristic, false) }
    }

    private fun requireConnection(address: String, sessionId: String): ManagedConnection {
        val connection = connections[address] ?: throw failure("Connect to the BLE device before using its characteristics.")
        require(connection.sessionId == sessionId) { "This BLE connection belongs to another runtime session." }
        require(connection.connected.isCompleted && !connection.connected.isCancelled) { "The BLE device is not connected." }
        return connection
    }

    private fun characteristic(connection: ManagedConnection, request: BluetoothCharacteristicRequest): BluetoothGattCharacteristic {
        val service = gatt(connection).getService(request.serviceUuid)
            ?: throw IllegalArgumentException("BLE service was not found: " + request.serviceUuid)
        return service.getCharacteristic(request.characteristicUuid)
            ?: throw IllegalArgumentException("BLE characteristic was not found: " + request.characteristicUuid)
    }

    private fun gatt(connection: ManagedConnection): BluetoothGatt = connection.gatt
        ?: throw failure("The BLE connection is no longer available.")

    @SuppressLint("MissingPermission")
    private fun closeConnection(address: String, connection: ManagedConnection) {
        connections.remove(address, connection)
        notifications.entries.filter { it.value.address == address && it.value.sessionId == connection.sessionId }
            .forEach { notifications.remove(it.key) }
        connection.fail(failure("The BLE connection was closed."))
        connection.gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
    }

    private fun scanner(): BluetoothLeScanner {
        val bluetooth = requireAdapter()
        if (!bluetooth.isEnabled) throw settingsRequired()
        return bluetooth.bluetoothLeScanner ?: throw failure("This device does not expose a BLE scanner.")
    }

    private fun requireAdapter(): BluetoothAdapter = adapter ?: throw RuntimeCapabilityException(
        RuntimeBridgeError("UNSUPPORTED_DEVICE", "This device has no Bluetooth adapter.", capabilityId, recoverable = false)
    )

    private fun permissionDenied() = RuntimeCapabilityException(
        RuntimeBridgeError("PERMISSION_DENIED", "Bluetooth scanning permission is not granted.", capabilityId, recoverable = true, action = "request_again")
    )

    private fun settingsRequired() = RuntimeCapabilityException(
        RuntimeBridgeError("SPECIAL_FLOW_REQUIRED", "Bluetooth is turned off.", capabilityId, recoverable = true, action = "open_settings")
    )

    private fun failure(message: String) = RuntimeCapabilityException(
        RuntimeBridgeError("NATIVE_FAILURE", message, capabilityId, recoverable = true, action = "retry")
    )

    private fun timeout(message: String) = RuntimeCapabilityException(
        RuntimeBridgeError("TIMEOUT", message, capabilityId, recoverable = true, action = "retry")
    )

    private data class ScanSubscription(val sessionId: String, val scanner: BluetoothLeScanner, val callback: ScanCallback)
    private data class NotificationSubscription(
        val id: String,
        val sessionId: String,
        val address: String,
        val serviceUuid: UUID,
        val characteristicUuid: UUID
    )

    private data class PendingRead(val characteristicUuid: UUID, val deferred: CompletableDeferred<ByteArray>)
    private data class PendingWrite(val characteristicUuid: UUID, val deferred: CompletableDeferred<Unit>)
    private data class PendingDescriptor(val descriptorUuid: UUID, val deferred: CompletableDeferred<Unit>)

    private class ManagedConnection(
        val address: String,
        val sessionId: String,
        private val eventBus: RuntimeEventBus,
        private val notificationIds: (UUID) -> List<String>
    ) {
        @Volatile var gatt: BluetoothGatt? = null
        @Volatile var services: List<Map<String, Any?>>? = null
        @Volatile var discovery: CompletableDeferred<List<Map<String, Any?>>>? = null
        @Volatile var read: PendingRead? = null
        @Volatile var write: PendingWrite? = null
        @Volatile var descriptor: PendingDescriptor? = null
        val connected = CompletableDeferred<Unit>()
        val operations = Mutex()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    connected.complete(Unit)
                    return
                }
                val error = RuntimeCapabilityException(RuntimeBridgeError(
                    "NATIVE_FAILURE",
                    "BLE connection ended (status=" + status + ", state=" + newState + ").",
                    "bluetooth_le",
                    recoverable = true,
                    action = "retry"
                ))
                fail(error)
                eventBus.emit(RuntimeBridgeEvent("ble_connection:" + address, mapOf("state" to "disconnected", "status" to status)))
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val pending = discovery ?: return
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pending.complete(gatt.services.map(::serviceInfo))
                } else {
                    pending.completeExceptionally(gattFailure("BLE service discovery failed (status=" + status + ")."))
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                completeRead(characteristic, characteristic.value ?: byteArrayOf(), status)
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                completeRead(characteristic, value, status)
            }

            private fun completeRead(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                val pending = read ?: return
                if (pending.characteristicUuid != characteristic.uuid) return
                if (status == BluetoothGatt.GATT_SUCCESS) pending.deferred.complete(value.copyOf())
                else pending.deferred.completeExceptionally(gattFailure("BLE characteristic read failed (status=" + status + ")."))
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                val pending = write ?: return
                if (pending.characteristicUuid != characteristic.uuid) return
                if (status == BluetoothGatt.GATT_SUCCESS) pending.deferred.complete(Unit)
                else pending.deferred.completeExceptionally(gattFailure("BLE characteristic write failed (status=" + status + ")."))
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                emitValue(characteristic, characteristic.value ?: byteArrayOf())
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                emitValue(characteristic, value)
            }

            private fun emitValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                notificationIds(characteristic.uuid).forEach { id ->
                    eventBus.emit(RuntimeBridgeEvent(id, mapOf(
                        "address" to address,
                        "characteristicUuid" to characteristic.uuid.toString(),
                        "valueBase64" to BluetoothValuePolicy.encode(value)
                    )))
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val pending = this@ManagedConnection.descriptor ?: return
                if (pending.descriptorUuid != descriptor.uuid) return
                if (status == BluetoothGatt.GATT_SUCCESS) pending.deferred.complete(Unit)
                else pending.deferred.completeExceptionally(gattFailure("BLE notification descriptor write failed (status=" + status + ")."))
            }
        }

        fun fail(error: Throwable) {
            connected.completeExceptionally(error)
            discovery?.completeExceptionally(error)
            read?.deferred?.completeExceptionally(error)
            write?.deferred?.completeExceptionally(error)
            descriptor?.deferred?.completeExceptionally(error)
        }

        private fun gattFailure(message: String) = RuntimeCapabilityException(
            RuntimeBridgeError("NATIVE_FAILURE", message, "bluetooth_le", recoverable = true, action = "retry")
        )
    }

    private companion object {
        val CLIENT_CONFIGURATION_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val OPERATION_TIMEOUT_MS = 15_000L

        fun serviceInfo(service: BluetoothGattService): Map<String, Any?> = mapOf(
            "uuid" to service.uuid.toString(),
            "type" to service.type,
            "characteristics" to service.characteristics.map { characteristic ->
                mapOf(
                    "uuid" to characteristic.uuid.toString(),
                    "properties" to characteristic.properties,
                    "read" to (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0),
                    "write" to (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0),
                    "writeWithoutResponse" to (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0),
                    "notify" to (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0),
                    "indicate" to (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                )
            }
        )
    }
}

private fun Map<String, Any?>.requiredString(vararg keys: String): String = keys.asSequence()
    .mapNotNull { this[it] as? String }
    .firstOrNull { it.isNotBlank() }
    ?.trim()
    ?: throw IllegalArgumentException(keys.first() + " is required.")

private fun Map<String, Any?>.requiredUuid(vararg keys: String): UUID = runCatching {
    UUID.fromString(requiredString(*keys))
}.getOrElse { throw IllegalArgumentException(keys.first() + " must be a valid UUID.") }

private fun Map<String, Any?>.requestValues(): Map<String, Any?> {
    val nested = this["request"] as? Map<*, *> ?: return this
    return nested.entries.associate { it.key.toString() to it.value }
}

private fun Any?.asUuidList(): List<UUID> = (this as? List<*>)?.map {
    runCatching { UUID.fromString(it as? String ?: "") }
        .getOrElse { throw IllegalArgumentException("serviceUuids must contain valid UUID strings.") }
}.orEmpty()
