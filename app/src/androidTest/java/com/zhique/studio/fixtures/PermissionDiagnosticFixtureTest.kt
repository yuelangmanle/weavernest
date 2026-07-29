package com.zhique.studio.fixtures

import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionDiagnosticFixtureTest {
    @Test
    fun `permission diagnostic fixture retains the user supplied hash and declared P0 capabilities`() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val html = context.assets.open("permission-diagnostic.html").bufferedReader().use { it.readText() }
        val expected = context.assets.open("permission-diagnostic.sha256").bufferedReader().use { it.readLine().substringBefore(' ') }
        val actual = MessageDigest.getInstance("SHA-256").digest(html.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }

        assertEquals(expected, actual)
        setOf("camera", "geolocation", "storage", "notification", "contacts", "microphone", "clipboard", "vibrate", "sensor", "config")
            .forEach { capability -> assertTrue(html.contains(capability), "Fixture is missing $capability") }
    }

    @Test
    fun `permission diagnostic checks Weaver synchronously before its page initialization`() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val html = context.assets.open("permission-diagnostic.html").bufferedReader().use { it.readText() }
        val hasWeaver = html.indexOf("const hasWeaver = typeof window.weaver")
        val initialization = html.indexOf("async function init()")

        assertTrue(hasWeaver >= 0)
        assertTrue(initialization >= 0)
        assertTrue(hasWeaver < initialization)
    }
}
