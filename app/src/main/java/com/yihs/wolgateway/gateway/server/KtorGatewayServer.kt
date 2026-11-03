package com.yihs.wolgateway.gateway.server

import io.ktor.server.application.Application
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import kotlinx.serialization.json.Json
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.usecase.CheckDeviceStatusUseCase
import com.yihs.wolgateway.domain.usecase.WakeDeviceUseCase
import com.yihs.wolgateway.gateway.auth.ApiTokenStore
import com.yihs.wolgateway.gateway.routing.configureApiErrorHandling
import com.yihs.wolgateway.gateway.routing.configureApiRoutes
import com.yihs.wolgateway.diagnostics.DiagnosticsProvider
import com.yihs.wolgateway.network.discovery.NetworkDiscovery
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket

@Singleton
class KtorGatewayServer @Inject constructor(
    private val repository: DeviceRepository,
    private val tokenStore: ApiTokenStore,
    private val wakeDevice: WakeDeviceUseCase,
    private val checkDeviceStatus: CheckDeviceStatusUseCase,
    private val diagnosticsProvider: Provider<DiagnosticsProvider>,
    private val networkDiscovery: NetworkDiscovery,
) : GatewayServer {
    private val lock = Any()
    private var engine: EmbeddedServer<*, *>? = null

    private val _state = MutableStateFlow(ServerState.STOPPED)
    override val state = _state.asStateFlow()

    private var config: GatewayServerConfig = GatewayServerConfig()

    override fun configure(config: GatewayServerConfig) {
        synchronized(lock) {
            check(_state.value == ServerState.STOPPED) { "Stop the API server before changing configuration" }
            this.config = config
        }
    }

    override fun start() {
        synchronized(lock) {
            if (_state.value == ServerState.RUNNING) return
            // CIO reports some Android bind failures from its worker coroutine. Probe first so
            // invalid or unavailable ports remain a recoverable UI error instead of a process crash.
            ServerSocket(config.port, 0, InetAddress.getByName(LOOPBACK_ADDRESS)).use { }
            engine = embeddedServer(
                CIO,
                host = LOOPBACK_ADDRESS,
                port = config.port,
                watchPaths = emptyList(),
            ) {
                install(ContentNegotiation) {
                    json(Json { encodeDefaults = true; ignoreUnknownKeys = false })
                }
                configureApiErrorHandling()
                 configureApiRoutes(
                     repository = repository,
                     tokenStore = tokenStore,
                     wakeDevice = wakeDevice,
                     checkDeviceStatus = checkDeviceStatus,
                     diagnosticsProvider = diagnosticsProvider.get(),
                     networkDiscovery = networkDiscovery,
                 )
            }
                .also {
                    try {
                        it.start(wait = false)
                        runBlocking(Dispatchers.IO) { it.engine.resolvedConnectors() }
                    } catch (error: Exception) {
                        it.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
                        engine = null
                        _state.value = ServerState.STOPPED
                        throw error
                    }
                }
            _state.value = ServerState.RUNNING
        }
    }

    override fun stop() {
        synchronized(lock) {
            engine?.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
            engine = null
            _state.value = ServerState.STOPPED
        }
    }

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"
    }
}
