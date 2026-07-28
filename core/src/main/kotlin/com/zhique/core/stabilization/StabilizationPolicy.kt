package com.zhique.core.stabilization

data class BackNavigationState(
    val hasTransientUi: Boolean,
    val canNavigateUp: Boolean,
    val isProjectRoot: Boolean,
    val exitArmed: Boolean
)

enum class BackNavigationAction {
    CloseTransientUi,
    NavigateUp,
    ReturnHome,
    ArmExit,
    ExitApplication
}

object BackNavigationPolicy {
    fun decide(state: BackNavigationState): BackNavigationAction = when {
        state.hasTransientUi -> BackNavigationAction.CloseTransientUi
        state.canNavigateUp -> BackNavigationAction.NavigateUp
        state.isProjectRoot -> BackNavigationAction.ReturnHome
        state.exitArmed -> BackNavigationAction.ExitApplication
        else -> BackNavigationAction.ArmExit
    }
}

sealed interface ApilotAvailability {
    data class InstalledCompatible(val versionName: String, val versionCode: Long) : ApilotAvailability
    data class InstalledIncompatible(val versionName: String?) : ApilotAvailability
    data object InstalledDisabled : ApilotAvailability
    data object NotInstalled : ApilotAvailability
}

object ApilotAvailabilityPolicy {
    fun classify(
        packageVisible: Boolean,
        applicationEnabled: Boolean,
        supportsPick: Boolean,
        supportsImport: Boolean,
        versionName: String?,
        versionCode: Long
    ): ApilotAvailability = when {
        !packageVisible -> ApilotAvailability.NotInstalled
        !applicationEnabled -> ApilotAvailability.InstalledDisabled
        supportsPick && supportsImport -> ApilotAvailability.InstalledCompatible(versionName.orEmpty(), versionCode)
        else -> ApilotAvailability.InstalledIncompatible(versionName)
    }
}
