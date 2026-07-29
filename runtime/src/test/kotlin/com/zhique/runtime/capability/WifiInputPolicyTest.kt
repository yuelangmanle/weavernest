package com.zhique.runtime.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WifiInputPolicyTest {
    @Test
    fun `Wi-Fi connection requests require a nonblank SSID`() {
        assertEquals("织雀测试网", WifiInputPolicy.requireSsid(" 织雀测试网 "))
        assertFailsWith<IllegalArgumentException> {
            WifiInputPolicy.requireSsid(" ")
        }
    }
}
