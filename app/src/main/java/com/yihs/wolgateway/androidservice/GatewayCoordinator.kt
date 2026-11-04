package com.yihs.wolgateway.androidservice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.gateway.server.GatewayServerConfig
import com.yihs.wolgateway.gateway.server.ServerState
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.root.RootSupervisor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
    private val gatewayServer: GatewayServer,
    private val tunnelController: TunnelController,
    private val rootSupervisor: RootSupervisor,
    private val serviceState: GatewayServiceState,
    private val modeState: GatewayServiceModeState,
) {
    private val lifecycleMutex = Mutex()

    suspend fun startFromPreferences(): Boolean = lifecycleMutex.withLock {
        val values = preferences.data.first()
        val rootRequested = values[GatewayStartupPreferences.ROOT_MODE_ENABLED] == true
        val rootConsented = values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true
        val port = values[API_PORT] ?: DEFAULT_API_PORT
        val startApi = values[SERVER_ENABLED] == true || values[GatewayStartupPreferences.AUTO_START_API] == true
        val startTunnel = values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true
        if (!startApi && !startTunnel) {
            modeState.set(GatewayServiceMode.STANDARD)
            return@withLock false
        }

        val rootStarted = rootRequested && rootConsented && rootSupervisor.start(
                consentGiven = rootConsented,
                apiPort = port,
                serviceAlreadyStarted = serviceState.running.value,
            )
        modeState.set(if (rootStarted) GatewayServiceMode.ROOT else GatewayServiceMode.STANDARD)

        if (gatewayServer.state.value != ServerState.RUNNING) {
            gatewayServer.configure(GatewayServerConfig(port))
            gatewayServer.start()
        }
        if (startTunnel && tunnelController.state.value.tokenConfigured) {
            tunnelController.connect(localApiOrigin(port))
        }
        true
    }

    suspend fun hasDesiredRuntime(): Boolean {
        val values = preferences.data.first()
        return values[SERVER_ENABLED] == true ||
            values[GatewayStartupPreferences.AUTO_START_API] == true ||
            values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true
    }

    suspend fun transitionMode(rootEnabled: Boolean, rootConsented: Boolean): Boolean =
        lifecycleMutex.withLock {
            val before = preferences.data.first()
            val shouldRun = before[SERVER_ENABLED] == true ||
                before[GatewayStartupPreferences.AUTO_START_API] == true ||
                before[GatewayStartupPreferences.AUTO_START_TUNNEL] == true
            val port = before[API_PORT] ?: DEFAULT_API_PORT

            rootSupervisor.stop()
            tunnelController.disconnectAndWait()
            gatewayServer.stop()
            GatewayService.stop(context)
            val stopped = withTimeoutOrNull(SERVICE_STOP_TIMEOUT_MS) {
                serviceState.running.filter { !it }.first()
                true
            } == true

            preferences.edit {
                it[GatewayStartupPreferences.ROOT_MODE_ENABLED] = rootEnabled
                it[GatewayStartupPreferences.ROOT_MODE_CONSENTED] = rootConsented
            }
            if (!shouldRun || !stopped) {
                modeState.set(GatewayServiceMode.STANDARD)
                return@withLock stopped
            }

            if (rootEnabled) {
                val started = rootSupervisor.start(
                    consentGiven = rootConsented,
                    apiPort = port,
                    serviceAlreadyStarted = false,
                )
                if (!started) {
                    preferences.edit {
                        it[GatewayStartupPreferences.ROOT_MODE_ENABLED] = false
                        it[GatewayStartupPreferences.ROOT_MODE_CONSENTED] = false
                    }
                    GatewayService.start(context)
                    modeState.set(GatewayServiceMode.STANDARD)
                    return@withLock false
                }
                modeState.set(GatewayServiceMode.ROOT)
            } else {
                GatewayService.start(context)
                modeState.set(GatewayServiceMode.STANDARD)
            }
            true
        }

    suspend fun reconnectTunnelIfEnabled() = lifecycleMutex.withLock {
        val values = preferences.data.first()
        if (values[AUTO_RECONNECT] != true) return@withLock
        if (values[GatewayStartupPreferences.AUTO_START_TUNNEL] != true ||
            gatewayServer.state.value != ServerState.RUNNING ||
            !tunnelController.state.value.tokenConfigured
        ) return@withLock

        val port = values[API_PORT] ?: DEFAULT_API_PORT
        tunnelController.reconnect(localApiOrigin(port))
    }

    fun onNetworkLost() {
        tunnelController.onNetworkLost()
    }

    fun stop() {
        rootSupervisor.stop()
        modeState.set(GatewayServiceMode.STANDARD)
        tunnelController.disconnect()
        gatewayServer.stop()
    }

    private companion object {
        val API_PORT = intPreferencesKey("api_port")
        val SERVER_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("api_server_enabled")
        val AUTO_RECONNECT = androidx.datastore.preferences.core.booleanPreferencesKey("tunnel_auto_reconnect")
        const val DEFAULT_API_PORT = 8080
        const val SERVICE_STOP_TIMEOUT_MS = 5_000L

        fun localApiOrigin(port: Int): String = "127.0.0.1:$port"
    }
}
