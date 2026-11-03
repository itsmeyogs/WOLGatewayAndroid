package com.yihs.wolgateway.androidservice

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayCoordinator @Inject constructor(
    private val preferences: DataStore<Preferences>,
    private val gatewayServer: GatewayServer,
    private val tunnelController: TunnelController,
    private val rootSupervisor: RootSupervisor,
) {
    private val lifecycleMutex = Mutex()

    suspend fun startFromPreferences(): Boolean = lifecycleMutex.withLock {
        val values = preferences.data.first()
        val rootRequested = values[GatewayStartupPreferences.ROOT_MODE_ENABLED] == true
        val rootConsented = values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true
        val port = values[API_PORT] ?: DEFAULT_API_PORT
        if (rootRequested) rootSupervisor.start(rootConsented, port)
        val startApi = values[SERVER_ENABLED] == true || values[GatewayStartupPreferences.AUTO_START_API] == true
        val startTunnel = values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true
        if (!startApi && !startTunnel) return@withLock false

        if (gatewayServer.state.value != ServerState.RUNNING) {
            gatewayServer.configure(GatewayServerConfig(port))
            gatewayServer.start()
        }
        if (startTunnel && tunnelController.state.value.tokenConfigured) {
            tunnelController.connect(localApiOrigin(port))
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
        tunnelController.disconnect()
        gatewayServer.stop()
    }

    private companion object {
        val API_PORT = intPreferencesKey("api_port")
        val SERVER_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("api_server_enabled")
        val AUTO_RECONNECT = androidx.datastore.preferences.core.booleanPreferencesKey("tunnel_auto_reconnect")
        const val DEFAULT_API_PORT = 8080

        fun localApiOrigin(port: Int): String = "127.0.0.1:$port"
    }
}
