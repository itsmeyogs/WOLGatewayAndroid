package com.yihs.wolgateway.domain.model

data class WolSendResult(
    val result: WolResult,
    val latencyMs: Long?,
    val reason: String,
)
