package com.yihs.wolgateway.gateway.server

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtorGatewayServer @Inject constructor() : GatewayServer {
    private val lock = Any()
    private var engine: EmbeddedServer<*, *>? = null

    override var state: ServerState = ServerState.STOPPED
        private set

    override fun start() {
        synchronized(lock) {
            if (state == ServerState.RUNNING) return
            engine = embeddedServer(CIO, port = 0, module = Application::foundationModule)
                .also { it.start(wait = false) }
            state = ServerState.RUNNING
        }
    }

    override fun stop() {
        synchronized(lock) {
            engine?.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
            engine = null
            state = ServerState.STOPPED
        }
    }
}

private fun Application.foundationModule() = Unit
