package com.yihs.wolgateway.root

interface RootCommandExecutor {
    fun verify(): Boolean
    fun startGatewayService(): Boolean
    fun stopGatewayService(): Boolean
    fun isGatewayServiceRunning(): Boolean
    fun isApiReady(port: Int): Boolean
    fun neighborMacs(): Map<String, String>
    fun resolveNeighborMac(address: String): String?
}
