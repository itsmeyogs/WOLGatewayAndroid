package com.yihs.wolgateway.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey

object GatewayStartupPreferences {
    val ROOT_MODE_ENABLED = booleanPreferencesKey("root_mode_enabled")
    val ROOT_MODE_CONSENTED = booleanPreferencesKey("root_mode_consented")
    val AUTO_START_API = booleanPreferencesKey("auto_start_api_service")
    val AUTO_START_TUNNEL = booleanPreferencesKey("auto_start_tunnel")
}
