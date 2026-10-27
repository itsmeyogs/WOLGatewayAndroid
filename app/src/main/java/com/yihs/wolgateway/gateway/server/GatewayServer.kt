package com.yihs.wolgateway.gateway.server

import kotlinx.coroutines.flow.StateFlow

interface GatewayServer {
    fun configure(config: GatewayServerConfig)

    fun start()
    fun stop()
    val state: StateFlow<ServerState>
}
