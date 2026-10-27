package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.WolResult
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponse(
    val id: Long,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String,
    val wolPort: Int,
    val statusCheckMethod: StatusCheckMethod,
    val statusCheckPort: Int,
    val status: DeviceStatus,
    val lastCheckAt: Long?,
    val lastCheckLatencyMs: Long?,
    val lastCheckReason: String?,
    val lastWolResult: WolResult,
    val lastWolAt: Long?,
)

fun Device.toResponse() = DeviceResponse(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    broadcastAddress = broadcastAddress,
    wolPort = wolPort,
    statusCheckMethod = statusCheckMethod,
    statusCheckPort = statusCheckPort,
    status = status,
    lastCheckAt = lastCheckAt,
    lastCheckLatencyMs = lastCheckLatencyMs,
    lastCheckReason = lastCheckReason,
    lastWolResult = lastWolResult,
    lastWolAt = lastWolAt,
)
