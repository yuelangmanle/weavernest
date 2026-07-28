package com.zhique.core.stabilization

import com.zhique.core.runtime.WeaverBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StabilizationPolicyTest {
    @Test
    fun `back closes transient UI before navigating away`() {
        val action = BackNavigationPolicy.decide(
            BackNavigationState(
                hasTransientUi = true,
                canNavigateUp = true,
                isProjectRoot = true,
                exitArmed = false
            )
        )

        assertEquals(BackNavigationAction.CloseTransientUi, action)
    }

    @Test
    fun `back from a project root returns home instead of exiting`() {
        val action = BackNavigationPolicy.decide(
            BackNavigationState(
                hasTransientUi = false,
                canNavigateUp = false,
                isProjectRoot = true,
                exitArmed = false
            )
        )

        assertEquals(BackNavigationAction.ReturnHome, action)
    }

    @Test
    fun `home requires a second back action before exiting`() {
        val first = BackNavigationPolicy.decide(BackNavigationState(false, false, false, false))
        val second = BackNavigationPolicy.decide(BackNavigationState(false, false, false, true))

        assertEquals(BackNavigationAction.ArmExit, first)
        assertEquals(BackNavigationAction.ExitApplication, second)
    }

    @Test
    fun `Apilot is incompatible when installed without required V2 actions`() {
        val availability = ApilotAvailabilityPolicy.classify(
            packageVisible = true,
            applicationEnabled = true,
            supportsPick = true,
            supportsImport = false,
            versionName = "1.0.0",
            versionCode = 10L
        )

        assertEquals(ApilotAvailability.InstalledIncompatible("1.0.0"), availability)
    }

    @Test
    fun `Apilot is compatible only when package and both V2 actions are visible`() {
        val availability = ApilotAvailabilityPolicy.classify(
            packageVisible = true,
            applicationEnabled = true,
            supportsPick = true,
            supportsImport = true,
            versionName = "2.0.0",
            versionCode = 20L
        )

        assertEquals(ApilotAvailability.InstalledCompatible("2.0.0", 20L), availability)
    }

    @Test
    fun `runtime bootstrap exposes the attachment APIs before page scripts execute`() {
        val script = WeaverBootstrap.documentStartScript()

        assertTrue(script.contains("window.weaver"))
        assertTrue(script.contains("apiVersion"))
        assertTrue(script.contains("camera.capture"))
        assertTrue(script.contains("geolocation.getCurrentPosition"))
        assertTrue(script.contains("storage.writeFile"))
        assertTrue(script.contains("notification.show"))
        assertTrue(script.contains("microphone.record"))
        assertTrue(script.contains("sensor.subscribe"))
        assertTrue(script.contains("config.get"))
    }

    @Test
    fun `fallback bootstrap is inserted before the first project script`() {
        val html = "<html><head><script>window.userStarted = true;</script></head><body></body></html>"
        val injected = WeaverBootstrap.injectIntoHtml(html)

        assertTrue(injected.indexOf("window.weaver") < injected.indexOf("window.userStarted"))
    }
}
