package com.yihs.wolgateway.gateway.server

data class GatewayServerConfig(
    val port: Int = 8080,
) {
    init {
        require(port in 1024..65_535) { "API port must be between 1024 and 65535" }
    }
}
