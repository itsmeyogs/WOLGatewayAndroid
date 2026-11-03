package com.yihs.wolgateway.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.yihs.wolgateway.R
import com.yihs.wolgateway.androidservice.GatewayService
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.controller.TunnelControllerError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.yihs.wolgateway.root.RootCapabilityDetector
import com.yihs.wolgateway.root.RootCapability

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
    tunnelController: TunnelController,
    private val rootDetector: RootCapabilityDetector,
) : ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(preferences.data, tunnelController.state) { values, tunnel ->
                SettingsUiState(
                    autoStartApi = values[GatewayStartupPreferences.AUTO_START_API] == true,
                    autoStartTunnel = values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true,
                    tunnelTokenConfigured = tunnel.tokenConfigured,
                    tunnelErrorMessage = tunnel.userMessage,
                    tunnelError = when (tunnel.error) {
                        TunnelControllerError.RUNTIME_UNAVAILABLE -> R.string.event_tunnel_runtime_unavailable
                        TunnelControllerError.START_FAILED -> R.string.event_tunnel_start_failed
                        null -> null
                    },
                    batteryOptimizationIgnored = batteryOptimizationIgnored(),
                    batteryGuidance = batteryGuidance(),
                    rootCapability = rootDetector.detect(),
                    rootModeEnabled = values[GatewayStartupPreferences.ROOT_MODE_ENABLED] == true,
                    rootConsentGiven = values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true,
                )
            }.collect { nextState ->
                if (nextState.rootModeEnabled && nextState.rootCapability != RootCapability.AVAILABLE) {
                    preferences.edit {
                        it[GatewayStartupPreferences.ROOT_MODE_ENABLED] = false
                        it[GatewayStartupPreferences.ROOT_MODE_CONSENTED] = false
                    }
                }
                _state.value = nextState.copy(
                    rootModeEnabled = nextState.rootModeEnabled && nextState.rootCapability == RootCapability.AVAILABLE,
                    rootConsentGiven = nextState.rootConsentGiven && nextState.rootCapability == RootCapability.AVAILABLE,
                )
            }
        }
    }

    fun requestBatteryOptimizationExemption() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun refreshBatteryOptimizationStatus() {
        _state.value = _state.value.copy(batteryOptimizationIgnored = batteryOptimizationIgnored())
    }

    private fun batteryOptimizationIgnored(): Boolean =
        (context.getSystemService(PowerManager::class.java))?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun batteryGuidance(): Int? = when (android.os.Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> R.string.settings_battery_oem_xiaomi
        "oppo", "realme" -> R.string.settings_battery_oem_oppo
        "vivo" -> R.string.settings_battery_oem_vivo
        "samsung" -> R.string.settings_battery_oem_samsung
        else -> R.string.settings_battery_oem_generic
    }

    fun setAutoStartApi(enabled: Boolean) {
        viewModelScope.launch {
            preferences.edit {
                it[GatewayStartupPreferences.AUTO_START_API] = enabled
                if (!enabled) it[GatewayStartupPreferences.AUTO_START_TUNNEL] = false
            }
            if (enabled) GatewayService.start(context)
        }
    }

    fun setAutoStartTunnel(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !_state.value.tunnelTokenConfigured) return@launch
            preferences.edit {
                it[GatewayStartupPreferences.AUTO_START_TUNNEL] = enabled
                if (enabled) it[GatewayStartupPreferences.AUTO_START_API] = true
            }
            if (enabled) GatewayService.start(context)
        }
    }

    fun setRootMode(enabled: Boolean) {
        viewModelScope.launch {
            val capability = rootDetector.detect()
            if (enabled && capability != com.yihs.wolgateway.root.RootCapability.AVAILABLE) return@launch
            preferences.edit {
                it[GatewayStartupPreferences.ROOT_MODE_CONSENTED] = enabled
                it[GatewayStartupPreferences.ROOT_MODE_ENABLED] = enabled
            }
            // Re-enter the service lifecycle so the root request is made now,
            // not only on the next boot or manual service start.
            GatewayService.stop(context)
            if (enabled || _state.value.autoStartApi || _state.value.autoStartTunnel) {
                GatewayService.start(context)
            }
        }
    }
}
