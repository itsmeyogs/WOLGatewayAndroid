package com.yihs.wolgateway.gateway.routing

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.repository.DuplicateDeviceException
import com.yihs.wolgateway.domain.usecase.CheckDeviceStatusUseCase
import com.yihs.wolgateway.domain.usecase.WakeDeviceUseCase
import com.yihs.wolgateway.domain.validation.InvalidDeviceException
import com.yihs.wolgateway.diagnostics.DiagnosticsProvider
import com.yihs.wolgateway.gateway.auth.ApiTokenStore
import com.yihs.wolgateway.gateway.dto.ApiError
import com.yihs.wolgateway.gateway.dto.ApiErrorResponse
import com.yihs.wolgateway.gateway.dto.CreateDeviceRequest
import com.yihs.wolgateway.gateway.dto.DeviceResponse
import com.yihs.wolgateway.gateway.dto.DiscoveryResponse
import com.yihs.wolgateway.gateway.dto.HealthResponse
import com.yihs.wolgateway.gateway.dto.toResponse
import com.yihs.wolgateway.gateway.dto.StatusResponse
import com.yihs.wolgateway.gateway.dto.UpdateDeviceRequest
import com.yihs.wolgateway.gateway.dto.WakeResponse
import com.yihs.wolgateway.network.discovery.DiscoveryResults
import com.yihs.wolgateway.network.discovery.NetworkDiscovery
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

private const val API_PREFIX = "/api/v1"
private const val MAX_REQUEST_BYTES = 64 * 1024L
private const val AUTH_TIMEOUT_MS = 2_000L
private const val CRUD_TIMEOUT_MS = 15_000L
private const val DIAGNOSTICS_TIMEOUT_MS = 10_000L
private const val DISCOVERY_REQUEST_TIMEOUT_MS = 30_000L
private const val DEVICES_REFRESH_TIMEOUT_MS = 10_000L

fun Application.configureApiRoutes(
    repository: DeviceRepository,
    tokenStore: ApiTokenStore,
    wakeDevice: WakeDeviceUseCase,
    checkDeviceStatus: CheckDeviceStatusUseCase,
    diagnosticsProvider: DiagnosticsProvider? = null,
    networkDiscovery: NetworkDiscovery? = null,
) {
    val discoveryMutex = Mutex()
    val discoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var activeDiscovery: Deferred<List<com.yihs.wolgateway.network.discovery.DiscoveryResult>>? = null
    val authenticated = createRouteScopedPlugin("ApiAuthentication") {
        onCall { call ->
            val authorization = call.request.headers[HttpHeaders.Authorization]
            val supplied = authorization?.removePrefix("Bearer ")
                ?.takeIf { it != authorization }
            val configuredToken = withTimeoutOrNull(AUTH_TIMEOUT_MS) { tokenStore.read() }
            if (supplied == null || configuredToken == null || !constantTimeEquals(supplied, configuredToken)) {
                throw UnauthorizedRequestException()
            }
        }
    }
    routing {
        get("$API_PREFIX/health") { call.respond(HealthResponse()) }
        route(API_PREFIX) {
            route("/devices") {
                install(authenticated)
                get {
                    call.rejectOversizedRequest()
                    val response = withApiTimeout(CRUD_TIMEOUT_MS) {
                        val devices = repository.observeDevices().first()
                        withTimeoutOrNull(DEVICES_REFRESH_TIMEOUT_MS) {
                            coroutineScope {
                                devices.map { device ->
                                    async {
                                        try {
                                            checkDeviceStatus(device.id)
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            // Keep the last persisted status when one target check fails.
                                        }
                                    }
                                }.awaitAll()
                            }
                        }
                        repository.observeDevices().first().map(Device::toResponse)
                    }
                    call.respond(response)
                }
                post {
                    call.rejectOversizedRequest()
                    withApiTimeout(CRUD_TIMEOUT_MS) {
                        val request = call.receive<CreateDeviceRequest>()
                        val id = repository.addDevice(request.toDomain())
                        call.respond(HttpStatusCode.Created, repository.getDevice(id)!!.toResponse())
                    }
                }
                route("/{id}") {
                    get {
                        withApiTimeout(CRUD_TIMEOUT_MS) {
                            val id = call.deviceId()
                            val device = repository.getDevice(id) ?: throw DeviceNotFoundException(id)
                            call.respond(device.toResponse())
                        }
                    }
                    put {
                        call.rejectOversizedRequest()
                        withApiTimeout(CRUD_TIMEOUT_MS) {
                            val id = call.deviceId()
                            repository.updateDevice(call.receive<UpdateDeviceRequest>().toDomain(id))
                            call.respond(repository.getDevice(id)!!.toResponse())
                        }
                    }
                    delete {
                        withApiTimeout(CRUD_TIMEOUT_MS) {
                            val id = call.deviceId()
                            if (repository.getDevice(id) == null) throw DeviceNotFoundException(id)
                            repository.deleteDevice(id)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }
                    get("/status") {
                        withApiTimeout(CRUD_TIMEOUT_MS) {
                            val id = call.deviceId()
                            val result = checkDeviceStatus(id)
                            call.respond(StatusResponse(id, result.status, result.method, result.latencyMs, result.reason, repository.getDevice(id)?.lastCheckAt))
                        }
                    }
                    post("/wake") {
                        withApiTimeout(CRUD_TIMEOUT_MS) {
                            val id = call.deviceId()
                            val result = wakeDevice(id)
                            call.respond(WakeResponse(id, result.result, result.latencyMs, result.reason))
                        }
                    }
                }
            }
            route("/diagnostics") {
                install(authenticated)
                get {
                    val provider = diagnosticsProvider ?: throw IllegalStateException("Diagnostics unavailable")
                    withApiTimeout(DIAGNOSTICS_TIMEOUT_MS) {
                        call.respond(provider.snapshot().first().toResponse())
                    }
                }
            }
            route("/discovery") {
                install(authenticated)
                post("/scan") {
                    val discovery = networkDiscovery ?: throw IllegalStateException("Discovery unavailable")
                    val startedAt = System.nanoTime()
                    val results = try {
                        withTimeout(DISCOVERY_REQUEST_TIMEOUT_MS) {
                            val scan = discoveryMutex.withLock {
                                activeDiscovery?.takeIf { it.isActive } ?: discoveryScope.async {
                                    withTimeout(DISCOVERY_REQUEST_TIMEOUT_MS) { discovery.scan() }
                                }.also { created ->
                                    activeDiscovery = created
                                    created.invokeOnCompletion {
                                        discoveryScope.launch {
                                            discoveryMutex.withLock {
                                                if (activeDiscovery === created) activeDiscovery = null
                                            }
                                        }
                                    }
                                }
                            }
                            scan.await()
                        }
                    } catch (_: TimeoutCancellationException) {
                        throw DiscoveryTimeoutException()
                    }
                    val savedDevices = withApiTimeout(CRUD_TIMEOUT_MS) { repository.observeDevices().first() }
                    call.respond(
                        DiscoveryResponse(
                            durationMs = (System.nanoTime() - startedAt) / 1_000_000,
                            devices = DiscoveryResults.excludeSavedAddresses(results, savedDevices)
                                .map { it.toResponse() },
                        ),
                    )
                }
            }
        }
    }
}

private suspend inline fun <T> withApiTimeout(timeoutMs: Long, crossinline block: suspend () -> T): T = try {
    withTimeout(timeoutMs) { block() }
} catch (_: TimeoutCancellationException) {
    throw ApiOperationTimeoutException()
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

class DiscoveryTimeoutException : RuntimeException()

class ApiOperationTimeoutException : RuntimeException()

class UnauthorizedRequestException : RuntimeException()

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
