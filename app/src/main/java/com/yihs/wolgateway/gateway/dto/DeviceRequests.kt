package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.domain.model.StatusCheckMethod
import kotlinx.serialization.Serializable

@Serializable
data class CreateDeviceRequest(
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val wolPort: Int = 9,
    val statusCheckMethod: StatusCheckMethod = StatusCheckMethod.ICMP_AND_TCP,
    val statusCheckPort: Int = 3389,
)

@Serializable
data class UpdateDeviceRequest(
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val wolPort: Int = 9,
    val statusCheckMethod: StatusCheckMethod = StatusCheckMethod.ICMP_AND_TCP,
    val statusCheckPort: Int = 3389,
)
