package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BluetoothAddressPolicyTest {
    @Test
    fun `Bluetooth operations require a canonical hardware address`() {
        assertEquals("AA:BB:CC:DD:EE:FF", BluetoothAddressPolicy.requireAddress("aa:bb:cc:dd:ee:ff"))
        assertFailsWith<IllegalArgumentException> {
            BluetoothAddressPolicy.requireAddress("not-a-device")
        }
    }
}
