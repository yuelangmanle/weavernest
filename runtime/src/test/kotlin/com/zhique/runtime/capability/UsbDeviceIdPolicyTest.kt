package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsbDeviceIdPolicyTest {
    @Test
    fun `USB device IDs must be nonblank system names`() {
        assertEquals("/dev/bus/usb/001/002", UsbDeviceIdPolicy.requireId("/dev/bus/usb/001/002"))
        assertFailsWith<IllegalArgumentException> { UsbDeviceIdPolicy.requireId(" ") }
    }
}
