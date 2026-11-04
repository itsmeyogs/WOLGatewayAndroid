package com.yihs.wolgateway.gateway.routing

import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DuplicateDeviceException
import com.yihs.wolgateway.domain.validation.InvalidDeviceException
import com.yihs.wolgateway.gateway.dto.ApiError
import com.yihs.wolgateway.gateway.dto.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException

fun Application.configureApiErrorHandling() {
    install(StatusPages) {
        exception<DiscoveryTimeoutException> { call, _ ->
            call.respond(HttpStatusCode.GatewayTimeout, ApiErrorResponse(ApiError("discovery_timeout", "Device discovery timed out")))
        }
        exception<RequestRejectedException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(ApiError("request_too_large", "Request body is too large")))
        }
        exception<UnauthorizedRequestException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(ApiError("unauthorized", "Authentication required")))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("invalid_json", "Request body is invalid")))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("invalid_json", "Request body is invalid")))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("bad_request", "Request is invalid")))
        }
        exception<InvalidDeviceException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    ApiError(
                        code = "validation_failed",
                        message = "Device validation failed",
                        fields = cause.issues.associate { it.field.name to it.message.name },
                    ),
                ),
            )
        }
        exception<DuplicateDeviceException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiError("duplicate_device", "Device already exists", mapOf("field" to cause.field.name))))
        }
        exception<DeviceNotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ApiErrorResponse(ApiError("device_not_found", "Device not found")))
        }
        exception<IllegalArgumentException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiError("bad_request", "Request is invalid")))
        }
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, ApiErrorResponse(ApiError("internal_error", "Internal server error")))
        }
    }
}
