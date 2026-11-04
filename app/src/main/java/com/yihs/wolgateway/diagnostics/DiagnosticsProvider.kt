package com.yihs.wolgateway.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.yihs.wolgateway.androidservice.GatewayServiceState
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.gateway.server.ServerState
import com.yihs.wolgateway.root.RootCapability
import com.yihs.wolgateway.root.RootCapabilityDetector
import com.yihs.wolgateway.root.RootSupervisor
import com.yihs.wolgateway.root.RootSupervisorState
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.model.TunnelState
import com.yihs.wolgateway.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.NetworkInterface
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

class DiagnosticsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
    private val gatewayServer: GatewayServer,
    private val tunnelController: TunnelController,
    private val serviceState: GatewayServiceState,
    private val rootDetector: RootCapabilityDetector,
    private val rootSupervisor: RootSupervisor,
) {
    fun snapshot(): Flow<DiagnosticsSnapshot> = combine(
        preferences.data,
        gatewayServer.state,
        tunnelController.state,
        serviceState.running,
        rootSupervisor.stateFlow,
    ) { values, apiState, tunnel, serviceRunning, supervisorState ->
        val rootRequested = values[GatewayStartupPreferences.ROOT_MODE_ENABLED] == true &&
            values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true
        val rootCapability = if (rootRequested) {
            withContext(Dispatchers.IO) { rootDetector.detect() }
        } else {
            RootCapability.UNKNOWN
        }
        val selectedMode = if (rootRequested) DiagnosticsMode.ROOT else DiagnosticsMode.STANDARD
        val rootMode = rootRequested &&
            rootCapability == RootCapability.AVAILABLE &&
            supervisorState in setOf(
                com.yihs.wolgateway.root.RootSupervisorState.STARTING,
                com.yihs.wolgateway.root.RootSupervisorState.RUNNING,
            )
        val effectiveMode = if (rootMode) DiagnosticsMode.ROOT else DiagnosticsMode.STANDARD
        val apiRunning = apiState == ServerState.RUNNING
        val tunnelConfigured = tunnel.tokenConfigured
        val notification = notificationStatus(serviceRunning)
        val standard = listOf(
            item(R.string.diagnostics_service_running, serviceRunning.toRunningDisplay(), serviceRunning.status()),
            item(R.string.diagnostics_service_mode, if (apiRunning && tunnel.status == TunnelState.CONNECTED) R.string.diagnostics_api_tunnel else R.string.diagnostics_api_only, apiRunning.status()),
            item(R.string.diagnostics_foreground_service, serviceRunning.toRunningDisplay(), serviceRunning.status()),
            item(R.string.diagnostics_notifications, notification.first, notification.second.status()),
            item(R.string.diagnostics_api_autostart, (values[GatewayStartupPreferences.AUTO_START_API] == true).toDisplay(), DiagnosticStatus.PASS),
            item(R.string.diagnostics_tunnel_autostart, (values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true).toDisplay(), if (tunnelConfigured) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED),
            item(R.string.diagnostics_battery_optimization, batteryStatus(), batteryIgnored().status()),
            item(R.string.diagnostics_boot_recovery, R.string.diagnostics_best_effort_os_oem, DiagnosticStatus.WARN),
        )
        val root = listOf(
            item(R.string.diagnostics_root_capability, rootCapability.displayName(), when (rootCapability) {
                RootCapability.AVAILABLE -> DiagnosticStatus.PASS
                RootCapability.UNAVAILABLE -> DiagnosticStatus.WARN
                RootCapability.UNKNOWN -> DiagnosticStatus.WARN
            }),
            item(R.string.diagnostics_user_consent, (values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true).toDisplay(), if (rootMode) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED),
            item(R.string.diagnostics_privilege_check, if (rootMode) R.string.diagnostics_passed else R.string.diagnostics_not_checked, if (rootMode) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED),
            item(R.string.diagnostics_supervisor, supervisorState.displayName(), if (supervisorState == RootSupervisorState.RUNNING) DiagnosticStatus.PASS else DiagnosticStatus.WARN),
            item(R.string.diagnostics_root_features, if (rootMode) R.string.diagnostics_available else R.string.diagnostics_fallback_standard, if (rootMode) DiagnosticStatus.PASS else DiagnosticStatus.WARN),
            item(R.string.diagnostics_fallback, (supervisorState.name == "FALLBACK").toDisplay(), if (supervisorState.name == "FALLBACK") DiagnosticStatus.WARN else DiagnosticStatus.PASS),
        )
        DiagnosticsSnapshot(
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: context.getString(R.string.diagnostics_unknown),
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: context.getString(R.string.diagnostics_unknown),
            deviceClassification = classifyDevice(Build.FINGERPRINT, Build.MODEL),
            networkInterface = activeInterface(),
            api = item(R.string.diagnostics_api, if (apiRunning) R.string.diagnostics_running else R.string.diagnostics_stopped, apiRunning.status()),
            tunnel = item(R.string.diagnostics_tunnel, tunnel.status.displayName(), tunnel.status.diagnosticStatus()),
            gatewayService = item(R.string.diagnostics_gateway_service, serviceRunning.toRunningDisplay(), serviceRunning.status()),
            safeErrors = buildList {
                tunnel.userMessage?.let { add(context.getString(it)) }
                if (rootRequested && supervisorState.name == "FALLBACK") add(context.getString(R.string.diagnostics_root_fallback_error))
            }.take(MAX_ERRORS),
             mode = effectiveMode,
             standard = standard,
             root = root,
             selectedMode = selectedMode,
             effectiveMode = effectiveMode,
         )
    }

    private fun item(label: Int, value: Int, status: DiagnosticStatus) = DiagnosticItem(
        label = context.getString(label),
        value = context.getString(value),
        status = status,
    )

    private fun item(label: Int, value: String, status: DiagnosticStatus) = DiagnosticItem(
        label = context.getString(label),
        value = value,
        status = status,
    )

    private fun batteryIgnored(): Boolean = context.getSystemService(android.os.PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun batteryStatus(): String = context.getString(if (batteryIgnored()) R.string.diagnostics_exempt else R.string.diagnostics_enabled)

    private fun notificationStatus(running: Boolean): Pair<String, Boolean> {
        if (!running) return context.getString(R.string.diagnostics_not_active) to true
        val manager = context.getSystemService(NotificationManager::class.java)
        val enabled = Build.VERSION.SDK_INT < 33 || manager.areNotificationsEnabled()
        return context.getString(if (enabled) R.string.diagnostics_enabled else R.string.diagnostics_disabled) to enabled
    }

    private fun activeInterface(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .firstOrNull()?.name ?: context.getString(R.string.diagnostics_not_detected)
    }.getOrDefault(context.getString(R.string.diagnostics_not_detected))

    private companion object {
        const val MAX_ERRORS = 5
        fun Boolean.toDisplay() = if (this) R.string.diagnostics_enabled else R.string.diagnostics_disabled
        fun Boolean.toRunningDisplay() = if (this) R.string.diagnostics_running else R.string.diagnostics_stopped
        fun Boolean.status() = if (this) DiagnosticStatus.PASS else DiagnosticStatus.WARN
        fun RootCapability.displayName() = when (this) {
            RootCapability.AVAILABLE -> R.string.diagnostics_available
            RootCapability.UNAVAILABLE -> R.string.diagnostics_unavailable
            RootCapability.UNKNOWN -> R.string.diagnostics_unknown
        }
        fun RootSupervisorState.displayName() = when (this) {
            RootSupervisorState.STOPPED -> R.string.diagnostics_supervisor_stopped
            RootSupervisorState.STARTING -> R.string.diagnostics_supervisor_starting
            RootSupervisorState.RUNNING -> R.string.diagnostics_supervisor_running
            RootSupervisorState.RESTARTING -> R.string.diagnostics_supervisor_restarting
            RootSupervisorState.FALLBACK -> R.string.diagnostics_supervisor_fallback
        }
        fun TunnelState.displayName() = when (this) {
            TunnelState.NOT_CONFIGURED -> R.string.tunnel_status_not_configured
            TunnelState.DISCONNECTED -> R.string.tunnel_status_disconnected
            TunnelState.CONNECTING -> R.string.tunnel_status_connecting
            TunnelState.CONNECTED -> R.string.tunnel_status_connected
            TunnelState.RECONNECTING -> R.string.tunnel_status_reconnecting
            TunnelState.ERROR -> R.string.tunnel_status_error
        }
        fun TunnelState.diagnosticStatus() = when (this) {
            TunnelState.CONNECTED -> DiagnosticStatus.PASS
            TunnelState.ERROR -> DiagnosticStatus.FAIL
            TunnelState.NOT_CONFIGURED -> DiagnosticStatus.NOT_CONFIGURED
            else -> DiagnosticStatus.WARN
        }
    }
}
