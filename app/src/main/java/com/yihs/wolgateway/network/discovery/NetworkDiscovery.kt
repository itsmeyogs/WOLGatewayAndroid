package com.yihs.wolgateway.network.discovery

interface NetworkDiscovery {
    suspend fun scan(): List<DiscoveryResult>
}
