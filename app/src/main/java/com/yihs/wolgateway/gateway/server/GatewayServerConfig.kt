package com.yihs.wolgateway.gateway.server

data class GatewayServerConfig(
    val bindAddress: String = "127.0.0.1",
    val port: Int = 8080,
)
