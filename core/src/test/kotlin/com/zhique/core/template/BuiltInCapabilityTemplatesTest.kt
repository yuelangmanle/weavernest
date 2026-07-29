package com.zhique.core.template

import com.zhique.core.project.RuntimeApiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInCapabilityTemplatesTest {
    @Test
    fun every_visible_starter_has_its_own_runtime_action() {
        assertTrue(BuiltInCapabilityTemplates.all.size >= 10)
        BuiltInCapabilityTemplates.all.forEach { template ->
            assertTrue(template.html.contains("weaver.ready()"), template.id)
            assertTrue(template.html.contains("weaver."), template.id)
            assertTrue(!template.html.contains("保存测试数据"), template.id)
        }
    }

    @Test
    fun offline_form_is_the_only_verified_template_without_device_evidence() {
        assertEquals(listOf("forms"), BuiltInCapabilityTemplates.all.filter { it.capabilities.isEmpty() }.map { it.id })
    }

    @Test
    fun required_template_capabilities_are_canonical() {
        val expected = mapOf(
            "camera" to setOf("camera", "network"),
            "album" to setOf("media_images"),
            "music" to setOf("media_audio"),
            "recorder" to setOf("microphone", "storage"),
            "location" to setOf("geolocation"),
            "ble" to setOf("bluetooth_le"),
            "wifi" to setOf("wifi_scan", "network"),
            "hotspot" to setOf("local_hotspot"),
            "api" to setOf("network", "config")
        )
        expected.forEach { (id, capabilities) ->
            assertEquals(capabilities, BuiltInCapabilityTemplates.all.first { it.id == id }.capabilities)
        }
    }

    @Test
    fun template_runtime_calls_are_registered_and_implemented() {
        val calls = Regex("(?:window\\.)?weaver(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
        BuiltInCapabilityTemplates.all.forEach { template ->
            assertTrue(template.minimumApi >= 29, template.id)
            assertTrue(template.verificationScenario.isNotBlank(), template.id)
            calls.findAll(template.html).map { it.value }.forEach { raw ->
                val method = when (raw) {
                    "window.weaver.ready", "weaver.ready" -> "window.weaver.ready"
                    else -> raw.removePrefix("window.")
                }
                assertTrue(RuntimeApiCatalog.contains(method), template.id + " references unregistered " + method)
                assertTrue(RuntimeApiCatalog.isImplemented(method), template.id + " references unfinished " + method)
            }
        }
    }
}
