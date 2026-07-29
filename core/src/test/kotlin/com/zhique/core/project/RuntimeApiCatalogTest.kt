package com.zhique.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeApiCatalogTest {
    @Test
    fun `P0 catalog covers every capability exercised by the permission diagnostic`() {
        assertEquals(
            setOf(
                "camera",
                "geolocation",
                "storage",
                "notification",
                "contacts",
                "microphone",
                "clipboard",
                "haptics",
                "sensors",
                "config"
            ),
            RuntimeApiCatalog.p0CapabilityIds
        )
    }

    @Test
    fun `both prompt languages document the same versioned Runtime API`() {
        val chinese = PromptPack.default(PromptLanguage.ZhCn).renderForExternalModel("权限测试")
        val english = PromptPack.default(PromptLanguage.En).renderForExternalModel("Permission test")

        assertTrue(chinese.contains("Runtime ${RuntimeApiCatalog.apiVersion}"))
        assertTrue(english.contains("Runtime ${RuntimeApiCatalog.apiVersion}"))
        RuntimeApiCatalog.implementedPublicMethodNames.forEach { method ->
            assertTrue(chinese.contains(method), "Chinese prompt is missing $method")
            assertTrue(english.contains(method), "English prompt is missing $method")
        }
        assertTrue(!chinese.contains("weaver.camera.recordVideo"))
        assertTrue(!english.contains("weaver.camera.recordVideo"))
    }

    @Test
    fun `full catalog reserves every planned device capability with its canonical bridge method`() {
        val expected = setOf(
            "weaver.media.pickImages",
            "weaver.audio.play",
            "weaver.bluetooth.scan",
            "weaver.wifi.scan",
            "weaver.hotspot.startLocalOnly",
            "weaver.nfc.read",
            "weaver.network.status",
            "weaver.share.open",
            "weaver.system.openUrl",
            "weaver.biometric.authenticate",
            "weaver.speech.speak",
            "weaver.screenCapture.request",
            "weaver.usb.list",
            "weaver.background.schedule"
        )

        assertTrue(RuntimeApiCatalog.publicMethodNames.containsAll(expected))
        assertTrue(RuntimeApiCatalog.isImplemented("weaver.screenCapture.request"))
        assertEquals("bluetooth_le", RuntimeApiCatalog.capabilityForBridgeMethod("bluetooth.scan"))
        assertEquals("local_hotspot", RuntimeApiCatalog.capabilityForBridgeMethod("hotspot.startLocalOnly"))
    }

    @Test
    fun `BLE GATT read write and notification subscriptions are published as implemented methods`() {
        val expected = setOf(
            "weaver.bluetooth.read",
            "weaver.bluetooth.write",
            "weaver.bluetooth.subscribe",
            "weaver.bluetooth.unsubscribe"
        )

        assertTrue(RuntimeApiCatalog.publicMethodNames.containsAll(expected))
        expected.forEach { method ->
            assertTrue(RuntimeApiCatalog.isImplemented(method), "$method must not be advertised as a placeholder")
        }
        assertEquals("bluetooth_le", RuntimeApiCatalog.capabilityForBridgeMethod("bluetooth.unsubscribe"))
    }

    @Test
    fun `ordinary BLE and Wi-Fi capabilities retain their Android 10 compatibility metadata`() {
        val byId = CapabilityRegistry.all().associateBy { it.id }

        listOf("bluetooth_le", "bluetooth_classic", "wifi_scan", "wifi_connect", "local_hotspot").forEach { id ->
            assertEquals(29, byId.getValue(id).minimumApi, id)
        }
    }
}
