package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals

class NfcTagPolicyTest {
    @Test
    fun `NFC tag IDs are rendered as stable uppercase hexadecimal`() {
        assertEquals("01AF00", NfcTagPolicy.hex(byteArrayOf(0x01, 0xAF.toByte(), 0x00)))
    }
}
