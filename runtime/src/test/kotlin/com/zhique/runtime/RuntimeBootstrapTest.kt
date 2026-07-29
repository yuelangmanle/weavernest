package com.zhique.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeBootstrapTest {
    @Test
    fun `document bootstrap uses an asynchronous single-message bridge`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        assertTrue(script.contains("ZhiqueRuntime.postMessage"))
        assertTrue(script.contains("window.__weaverResolve"))
        assertTrue(script.contains("requestId"))
        assertTrue(script.contains("window.weaver"))
        assertFalse(script.contains("ZhiqueNative.call"))
    }

    @Test
    fun `document bootstrap exposes every catalogued P0 method before project scripts`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        assertTrue(script.contains("camera.capture"))
        assertTrue(script.contains("geolocation.getCurrentPosition"))
        assertTrue(script.contains("storage.writeFile"))
        assertTrue(script.contains("notification.show"))
        assertTrue(script.contains("microphone.record"))
        assertTrue(script.contains("sensor.subscribe"))
        assertTrue(script.contains("config.get"))
    }

    @Test
    fun `legacy sensor callers can cancel the promise returned while subscribing`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        assertTrue(script.contains("subscriptionPromise.unsubscribe"))
    }

    @Test
    fun `document bootstrap exposes implemented connection media system and screen capture namespaces`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        listOf("media:", "audio:", "bluetooth:", "wifi:", "hotspot:", "nfc:", "network:", "share:", "biometric:", "speech:", "usb:", "background:").forEach {
            namespace -> assertTrue(script.contains(namespace), "Missing $namespace")
        }
        assertTrue(script.contains("screenCapture:"))
    }

    @Test
    fun `connection subscriptions use the same event channel as sensor subscriptions`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        assertTrue(script.contains("subscriptionListeners"))
        assertTrue(script.contains("callbackSubscription('bluetooth.scan'"))
    }

    @Test
    fun `background task wrapper places the task in the native request envelope`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        assertTrue(script.contains("background.schedule', { task: task || {} }"))
    }

    @Test
    fun `system bootstrap exposes the implemented notification and haptics controls`() {
        val script = RuntimeBootstrap.documentStartScript("session-123")

        assertTrue(script.contains("notification.cancel"))
        assertTrue(script.contains("haptics.impact"))
    }
}
