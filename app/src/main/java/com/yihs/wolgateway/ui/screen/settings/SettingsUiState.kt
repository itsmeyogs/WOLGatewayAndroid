package com.yihs.wolgateway.ui.screen.settings

data class SettingsUiState(
    val autoStartApi: Boolean = false,
    val autoStartTunnel: Boolean = false,
    val tunnelTokenConfigured: Boolean = false,
    val tunnelErrorMessage: String? = null,
    val tunnelError: Int? = null,
    val batteryOptimizationIgnored: Boolean = false,
    val batteryGuidance: Int? = null,
)
