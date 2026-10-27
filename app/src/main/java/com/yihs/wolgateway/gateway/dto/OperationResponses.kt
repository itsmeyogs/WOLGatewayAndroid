package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.WolResult
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String = "ok")

@Serializable
data class StatusResponse(
    val deviceId: Long,
    val status: DeviceStatus,
    val method: StatusCheckMethod,
    val latencyMs: Long?,
    val reason: String,
    val checkedAt: Long?,
)

@Serializable
data class WakeResponse(
    val deviceId: Long,
    val result: WolResult,
    val latencyMs: Long?,
    val reason: String,
)
