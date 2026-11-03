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
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.model.TunnelState
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
        val rootMode = rootRequested && rootCapability == RootCapability.AVAILABLE
        val apiRunning = apiState == ServerState.RUNNING
        val tunnelConfigured = tunnel.tokenConfigured
        val notification = notificationStatus(serviceRunning)
        val standard = listOf(
            item("Service running", serviceRunning.toRunningDisplay(), serviceRunning.status()),
            item("Service mode", if (apiRunning && tunnel.status == TunnelState.CONNECTED) "API + tunnel" else "API only", apiRunning.status()),
            item("Foreground service", serviceRunning.toRunningDisplay(), serviceRunning.status()),
            item("Notifications", notification.first, notification.second.status()),
            item("API autostart", (values[GatewayStartupPreferences.AUTO_START_API] == true).toDisplay(), DiagnosticStatus.PASS),
            item("Tunnel autostart", (values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true).toDisplay(), if (tunnelConfigured) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED),
            item("Battery optimization", batteryStatus(), batteryIgnored().status()),
            item("Boot recovery", "best effort; OS/OEM dependent", DiagnosticStatus.WARN),
        )
        val root = listOf(
            item("Root capability", rootCapability.displayName(), when (rootCapability) {
                RootCapability.AVAILABLE -> DiagnosticStatus.PASS
                RootCapability.UNAVAILABLE -> DiagnosticStatus.WARN
                RootCapability.UNKNOWN -> DiagnosticStatus.WARN
            }),
            item("User consent", (values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true).toDisplay(), if (rootMode) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED),
            item("Privilege check", if (rootMode) "passed" else "not checked", if (rootMode) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED),
            item("Supervisor", supervisorState.name.lowercase(), if (supervisorState.name == "RUNNING") DiagnosticStatus.PASS else DiagnosticStatus.WARN),
            item("Root features", if (rootMode) "available" else "fallback to Standard Mode", if (rootMode) DiagnosticStatus.PASS else DiagnosticStatus.WARN),
            item("Fallback", (supervisorState.name == "FALLBACK").toDisplay(), if (supervisorState.name == "FALLBACK") DiagnosticStatus.WARN else DiagnosticStatus.PASS),
        )
        DiagnosticsSnapshot(
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown",
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            deviceClassification = classifyDevice(Build.FINGERPRINT, Build.MODEL),
            networkInterface = activeInterface(),
            api = item("API", if (apiRunning) "running" else "stopped", apiRunning.status()),
            tunnel = item("Tunnel", tunnel.status.displayName(), tunnel.status.diagnosticStatus()),
            gatewayService = item("GatewayService", serviceRunning.toRunningDisplay(), serviceRunning.status()),
            safeErrors = buildList {
                tunnel.userMessage?.let(::add)
                if (rootRequested && supervisorState.name == "FALLBACK") add("Root supervision fell back to Standard Mode")
            }.take(MAX_ERRORS),
            mode = if (rootMode) DiagnosticsMode.ROOT else DiagnosticsMode.STANDARD,
            standard = standard,
            root = root,
        )
    }

    private fun item(label: String, value: String, status: DiagnosticStatus) = DiagnosticItem(
        label = label,
        value = value,
        status = status,
    )

    private fun batteryIgnored(): Boolean = context.getSystemService(android.os.PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun batteryStatus(): String = if (batteryIgnored()) "exempt" else "enabled"

    private fun notificationStatus(running: Boolean): Pair<String, Boolean> {
        if (!running) return "not active" to true
        val manager = context.getSystemService(NotificationManager::class.java)
        val enabled = Build.VERSION.SDK_INT < 33 || manager.areNotificationsEnabled()
        return (if (enabled) "enabled" else "disabled") to enabled
    }

    private fun activeInterface(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .firstOrNull()?.name ?: "not detected"
    }.getOrDefault("not detected")

    private companion object {
        const val MAX_ERRORS = 5
        fun Boolean.toDisplay() = if (this) "enabled" else "disabled"
        fun Boolean.toRunningDisplay() = if (this) "running" else "stopped"
        fun Boolean.status() = if (this) DiagnosticStatus.PASS else DiagnosticStatus.WARN
        fun RootCapability.displayName() = when (this) {
            RootCapability.AVAILABLE -> "available"
            RootCapability.UNAVAILABLE -> "unavailable"
            RootCapability.UNKNOWN -> "unknown"
        }
        fun TunnelState.displayName() = name.lowercase().replace('_', ' ')
        fun TunnelState.diagnosticStatus() = when (this) {
            TunnelState.CONNECTED -> DiagnosticStatus.PASS
            TunnelState.ERROR -> DiagnosticStatus.FAIL
            TunnelState.NOT_CONFIGURED -> DiagnosticStatus.NOT_CONFIGURED
            else -> DiagnosticStatus.WARN
        }
    }
}
