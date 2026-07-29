package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BluetoothGattRequestPolicyTest {
    private val address = "aa:bb:cc:dd:ee:ff"
    private val service = "0000180d-0000-1000-8000-00805f9b34fb"
    private val characteristic = "00002a37-0000-1000-8000-00805f9b34fb"

    @Test
    fun characteristicRequestAcceptsFlatAndNestedBridgeEnvelopes() {
        val flat = BluetoothCharacteristicRequest.from(
            mapOf("id" to address, "serviceUuid" to service, "characteristicUuid" to characteristic)
        )
        val nested = BluetoothCharacteristicRequest.from(
            mapOf("request" to mapOf("address" to address, "service" to service, "characteristic" to characteristic))
        )

        assertEquals("AA:BB:CC:DD:EE:FF", flat.address)
        assertEquals(flat, nested)
    }

    @Test
    fun writeValuesAcceptExactlyOneBoundedUtf8OrBase64Representation() {
        assertContentEquals(
            "织雀".toByteArray(),
            BluetoothValuePolicy.decode(mapOf("text" to "织雀"))
        )
        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0x7f),
            BluetoothValuePolicy.decode(mapOf("valueBase64" to "AQJ/"))
        )
        assertFailsWith<IllegalArgumentException> {
            BluetoothValuePolicy.decode(mapOf("text" to "x", "valueBase64" to "eA=="))
        }
        assertFailsWith<IllegalArgumentException> {
            BluetoothValuePolicy.decode(mapOf("valueBase64" to "not base64"))
        }
    }
}
