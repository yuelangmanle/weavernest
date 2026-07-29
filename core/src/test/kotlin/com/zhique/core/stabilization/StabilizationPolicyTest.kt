package com.zhique.core.stabilization

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
    fun `Apilot launch failure remains distinct from an installation problem`() {
        val availability: ApilotAvailability = ApilotAvailability.LaunchFailed

        assertEquals(ApilotAvailability.LaunchFailed, availability)
        assertTrue(availability !is ApilotAvailability.NotInstalled)
    }

}
