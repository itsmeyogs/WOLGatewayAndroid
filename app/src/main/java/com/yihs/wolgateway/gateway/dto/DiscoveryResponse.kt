package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.network.discovery.DiscoveryResult
import kotlinx.serialization.Serializable

@Serializable
data class DiscoveryDeviceResponse(
    val ipAddress: String,
    val hostName: String?,
    val macAddress: String?,
    val openPorts: List<Int>,
)

@Serializable
data class DiscoveryResponse(
    val durationMs: Long,
    val devices: List<DiscoveryDeviceResponse>,
)

fun DiscoveryResult.toResponse() = DiscoveryDeviceResponse(
    ipAddress = address,
    hostName = hostName,
    macAddress = macAddress,
    openPorts = openPorts,
)
