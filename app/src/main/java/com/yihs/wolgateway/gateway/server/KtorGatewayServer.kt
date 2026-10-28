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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class KtorGatewayServer @Inject constructor(
    private val repository: DeviceRepository,
    private val tokenStore: ApiTokenStore,
    private val wakeDevice: WakeDeviceUseCase,
    private val checkDeviceStatus: CheckDeviceStatusUseCase,
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
            engine = embeddedServer(
                CIO,
                host = config.bindAddress,
                port = config.port,
                watchPaths = emptyList(),
            ) {
                install(ContentNegotiation) {
                    json(Json { encodeDefaults = true; ignoreUnknownKeys = false })
                }
                configureApiErrorHandling()
                configureApiRoutes(repository, tokenStore, wakeDevice, checkDeviceStatus)
            }
                .also { it.start(wait = false) }
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
}
