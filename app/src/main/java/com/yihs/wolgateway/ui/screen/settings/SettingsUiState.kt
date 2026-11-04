package com.yihs.wolgateway.ui.screen.settings

import com.yihs.wolgateway.root.RootCapability

data class SettingsUiState(
    val autoStartApi: Boolean = false,
    val autoStartTunnel: Boolean = false,
    val tunnelTokenConfigured: Boolean = false,
    val tunnelErrorMessage: Int? = null,
    val tunnelError: Int? = null,
    val batteryOptimizationIgnored: Boolean = false,
    val batteryGuidance: Int? = null,
    val rootCapability: RootCapability = RootCapability.UNKNOWN,
    val rootModeEnabled: Boolean = false,
    val rootConsentGiven: Boolean = false,
)
