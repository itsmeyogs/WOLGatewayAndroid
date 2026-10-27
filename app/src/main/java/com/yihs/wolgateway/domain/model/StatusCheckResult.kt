package com.yihs.wolgateway.domain.model

data class StatusCheckResult(
    val status: DeviceStatus,
    val method: StatusCheckMethod,
    val latencyMs: Long?,
    val reason: String,
)
