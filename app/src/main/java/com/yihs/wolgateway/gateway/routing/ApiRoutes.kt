package com.yihs.wolgateway.gateway.routing

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.repository.DuplicateDeviceException
import com.yihs.wolgateway.domain.usecase.CheckDeviceStatusUseCase
import com.yihs.wolgateway.domain.usecase.WakeDeviceUseCase
import com.yihs.wolgateway.domain.validation.InvalidDeviceException
import com.yihs.wolgateway.gateway.auth.ApiTokenStore
import com.yihs.wolgateway.gateway.dto.ApiError
import com.yihs.wolgateway.gateway.dto.ApiErrorResponse
import com.yihs.wolgateway.gateway.dto.CreateDeviceRequest
import com.yihs.wolgateway.gateway.dto.DeviceResponse
import com.yihs.wolgateway.gateway.dto.HealthResponse
import com.yihs.wolgateway.gateway.dto.StatusResponse
import com.yihs.wolgateway.gateway.dto.UpdateDeviceRequest
import com.yihs.wolgateway.gateway.dto.WakeResponse
import com.yihs.wolgateway.gateway.dto.toResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.intercept
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private const val API_PREFIX = "/api/v1"
private const val MAX_REQUEST_BYTES = 64 * 1024L

fun Application.configureApiRoutes(
    repository: DeviceRepository,
    tokenStore: ApiTokenStore,
    wakeDevice: WakeDeviceUseCase,
    checkDeviceStatus: CheckDeviceStatusUseCase,
) {
    routing {
        get("$API_PREFIX/health") { call.respond(HealthResponse()) }
        route(API_PREFIX) {
            route("/devices") {
                interceptAuthenticated(tokenStore)
                get {
                    call.rejectOversizedRequest()
                    call.respond(repository.observeDevices().first().map(Device::toResponse))
                }
                post {
                    call.rejectOversizedRequest()
                    val request = call.receive<CreateDeviceRequest>()
                    val id = repository.addDevice(request.toDomain())
                    call.respond(HttpStatusCode.Created, repository.getDevice(id)!!.toResponse())
                }
                route("/{id}") {
                    get {
                        val id = call.deviceId()
                        val device = repository.getDevice(id) ?: throw DeviceNotFoundException(id)
                        call.respond(device.toResponse())
                    }
                    put {
                        call.rejectOversizedRequest()
                        val id = call.deviceId()
                        repository.updateDevice(call.receive<UpdateDeviceRequest>().toDomain(id))
                        call.respond(repository.getDevice(id)!!.toResponse())
                    }
                    delete {
                        repository.deleteDevice(call.deviceId())
                        call.respond(HttpStatusCode.NoContent)
                    }
                    get("/status") {
                        val id = call.deviceId()
                        val result = checkDeviceStatus(id)
                        call.respond(StatusResponse(id, result.status, result.method, result.latencyMs, result.reason, repository.getDevice(id)?.lastCheckAt))
                    }
                    post("/wake") {
                        val id = call.deviceId()
                        val result = wakeDevice(id)
                        call.respond(WakeResponse(id, result.result, result.latencyMs, result.reason))
                    }
                }
            }
        }
    }
}

private fun io.ktor.server.routing.Route.interceptAuthenticated(tokenStore: ApiTokenStore) {
    intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
        val authorization = call.request.headers[HttpHeaders.Authorization]
        val supplied = authorization?.removePrefix("Bearer ")
            ?.takeIf { it != authorization }
        val configuredToken = tokenStore.read()
        if (supplied == null || configuredToken == null || !constantTimeEquals(supplied, configuredToken)) {
            call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(ApiError("unauthorized", "Authentication required")))
            finish()
        }
    }
}

private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
    left.toByteArray(Charsets.UTF_8),
    right.toByteArray(Charsets.UTF_8),
)

private suspend fun io.ktor.server.application.ApplicationCall.rejectOversizedRequest() {
    if (request.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > MAX_REQUEST_BYTES } == true) {
        respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(ApiError("request_too_large", "Request body is too large")))
        throw RequestRejectedException()
    }
}

class RequestRejectedException : RuntimeException()

private fun io.ktor.server.application.ApplicationCall.deviceId(): Long = parameters["id"]?.toLongOrNull()
    ?.takeIf { it > 0 } ?: throw IllegalArgumentException("Invalid device ID")

private fun CreateDeviceRequest.toDomain() = Device(
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    broadcastAddress = broadcastAddress,
    wolPort = wolPort,
    statusCheckMethod = statusCheckMethod,
    statusCheckPort = statusCheckPort,
)

private fun UpdateDeviceRequest.toDomain(id: Long) = Device(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    broadcastAddress = broadcastAddress,
    wolPort = wolPort,
    statusCheckMethod = statusCheckMethod,
    statusCheckPort = statusCheckPort,
)
