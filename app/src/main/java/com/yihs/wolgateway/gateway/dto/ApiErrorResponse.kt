package com.yihs.wolgateway.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val fields: Map<String, String> = emptyMap(),
)
