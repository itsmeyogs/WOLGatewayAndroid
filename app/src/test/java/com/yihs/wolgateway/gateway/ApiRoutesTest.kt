package com.yihs.wolgateway.gateway

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.StatusCheckResult
import com.yihs.wolgateway.domain.model.WolSendResult
import com.yihs.wolgateway.domain.model.WolResult
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.service.StatusChecker
import com.yihs.wolgateway.domain.service.WolSender
import com.yihs.wolgateway.domain.usecase.CheckDeviceStatusUseCase
import com.yihs.wolgateway.domain.usecase.WakeDeviceUseCase
import com.yihs.wolgateway.domain.validation.DeviceValidator
import com.yihs.wolgateway.network.discovery.DiscoveryResult
import com.yihs.wolgateway.network.discovery.NetworkDiscovery
import com.yihs.wolgateway.gateway.auth.ApiTokenStore
import com.yihs.wolgateway.gateway.routing.configureApiErrorHandling
import com.yihs.wolgateway.gateway.routing.configureApiRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRoutesTest {
    private val token = "test-token-with-at-least-32-characters"

    @Test
    fun healthIsPublicAndDeviceListRequiresBearerToken() = testApplication {
        val repository = FakeDeviceRepository()
        application { installTestApi(repository) }

        assertEquals(HttpStatusCode.OK, client.get("/api/v1/health").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/devices").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/devices") { bearer() }.status)
    }

    @Test
    fun createListAndDeleteDeviceUseRepository() = testApplication {
        val repository = FakeDeviceRepository()
        application { installTestApi(repository) }
        val body = """
            {"name":"Office PC","ipAddress":"192.168.1.20","macAddress":"AA:BB:CC:DD:EE:FF"}
        """.trimIndent()

        val created = client.post("/api/v1/devices") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val devices = client.get("/api/v1/devices") { bearer() }
        assertTrue(devices.bodyAsText().contains("Office PC"))
        assertTrue(devices.bodyAsText().contains("\"status\":\"ONLINE\""))
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/devices/1") { bearer() }.status)
    }

    @Test
    fun invalidDeviceReturnsStableValidationEnvelope() = testApplication {
        application { installTestApi(FakeDeviceRepository()) }
        val response = client.post("/api/v1/devices") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"\",\"ipAddress\":\"bad\",\"macAddress\":\"bad\"}")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("validation_failed"))
    }

    @Test
    fun protectedRoutesReturnExpectedNotFoundAndInvalidTokenResponses() = testApplication {
        application { installTestApi(FakeDeviceRepository()) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer wrong-token")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/devices/999") { bearer() }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/devices/999/status") { bearer() }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/devices/999/wake") { bearer() }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/devices/999") { bearer() }.status)
    }

    @Test
    fun updateAndOperationRoutesUseSharedRepositoryAndUseCases() = testApplication {
        val repository = FakeDeviceRepository()
        application { installTestApi(repository) }
        val body = """
            {"name":"Office PC","ipAddress":"192.168.1.20","macAddress":"AA:BB:CC:DD:EE:FF"}
        """.trimIndent()
        client.post("/api/v1/devices") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val updated = client.put("/api/v1/devices/1") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(body.replace("Office PC", "Updated PC"))
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertTrue(updated.bodyAsText().contains("Updated PC"))
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/devices/1/status") { bearer() }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/devices/1/wake") { bearer() }.status)
    }

    @Test
    fun discoveryWaitsForScanAndReturnsReachableResults() = testApplication {
        application {
            installTestApi(
                repository = FakeDeviceRepository(),
                networkDiscovery = FakeNetworkDiscovery(
                    listOf(DiscoveryResult("192.168.1.20", "Office PC", listOf(3389), null)),
                ),
            )
        }

        val response = client.post("/api/v1/discovery/scan") { bearer() }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("192.168.1.20"))
        assertTrue(response.bodyAsText().contains("Office PC"))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer() {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun io.ktor.server.application.Application.installTestApi(
        repository: FakeDeviceRepository,
        networkDiscovery: NetworkDiscovery = FakeNetworkDiscovery(emptyList()),
    ) {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        configureApiErrorHandling()
        configureApiRoutes(
            repository = repository,
            tokenStore = FakeTokenStore(token),
            wakeDevice = WakeDeviceUseCase(repository, FakeWolSender()),
            checkDeviceStatus = CheckDeviceStatusUseCase(repository, FakeStatusChecker()),
            networkDiscovery = networkDiscovery,
        )
    }
}

private class FakeNetworkDiscovery(
    private val result: List<DiscoveryResult>,
) : NetworkDiscovery {
    override suspend fun scan(): List<DiscoveryResult> = result
}

private class FakeTokenStore(private var value: String) : ApiTokenStore {
    override fun getOrCreate(): String = value
    override fun read(): String? = value.takeIf { it.isNotEmpty() }
    override fun createNew(): String = value
    override fun replace(token: String): String = token.also { value = it }
    override fun isConfigured(): Boolean = value.isNotEmpty()
}

private class FakeDeviceRepository : DeviceRepository {
    private val devices = MutableStateFlow<List<Device>>(emptyList())

    override fun observeDevices(): Flow<List<Device>> = devices
    override fun observeDevice(id: Long): Flow<Device?> = devices.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getDevice(id: Long): Device? = devices.value.firstOrNull { it.id == id }

    override suspend fun addDevice(device: Device): Long {
        DeviceValidator.requireValid(device)
        val id = (devices.value.maxOfOrNull { it.id } ?: 0) + 1
        devices.value = devices.value + device.copy(id = id)
        return id
    }

    override suspend fun updateDevice(device: Device) {
        DeviceValidator.requireValid(device)
        devices.value = devices.value.map { if (it.id == device.id) device else it }
    }

    override suspend fun deleteDevice(id: Long) {
        devices.value = devices.value.filterNot { it.id == id }
    }
}

private class FakeWolSender : WolSender {
    override suspend fun send(device: Device) = WolSendResult(WolResult.PACKET_SENT, 1, "packet_sent")
}

private class FakeStatusChecker : StatusChecker {
    override suspend fun check(ipAddress: String, method: StatusCheckMethod, port: Int) =
        StatusCheckResult(DeviceStatus.ONLINE, method, 1, "test")
}
