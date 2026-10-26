package com.yihs.wolgateway.gateway.server

interface GatewayServer {
    fun start()
    fun stop()
    val state: ServerState
}
