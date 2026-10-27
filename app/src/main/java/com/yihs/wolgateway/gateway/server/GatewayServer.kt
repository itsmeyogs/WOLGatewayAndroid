package com.yihs.wolgateway.gateway.server

interface GatewayServer {
    fun configure(config: GatewayServerConfig)

    fun start()
    fun stop()
    val state: ServerState
}
