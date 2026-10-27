package com.yihs.wolgateway.network.discovery

data class DiscoveryResult(
    val address: String,
    val hostName: String? = null,
    val openPorts: List<Int> = emptyList(),
)
