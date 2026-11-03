package com.yihs.wolgateway.domain

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.StatusCheckResult
import com.yihs.wolgateway.domain.model.WolResult
import com.yihs.wolgateway.domain.model.WolSendResult
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.service.StatusChecker
import com.yihs.wolgateway.domain.service.WolSender
import com.yihs.wolgateway.domain.usecase.CheckDeviceStatusUseCase
import com.yihs.wolgateway.domain.usecase.WakeDeviceUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UseCaseStateTest {
    @Test
    fun statusCheckPersistsStatusMetadata() = runTest {
        val repository = FakeRepository()
        val useCase = CheckDeviceStatusUseCase(repository, object : StatusChecker {
            override suspend fun check(ipAddress: String, method: StatusCheckMethod, port: Int) =
                StatusCheckResult(DeviceStatus.UNKNOWN, StatusCheckMethod.TCP, 12, "tcp_timeout")
        })

        val result = useCase(1)

        assertEquals(DeviceStatus.UNKNOWN, result.status)
        assertEquals(DeviceStatus.UNKNOWN, repository.device.status)
        assertEquals(12L, repository.device.lastCheckLatencyMs)
        assertEquals("tcp_timeout", repository.device.lastCheckReason)
        assertNotNull(repository.device.lastCheckAt)
    }

    @Test
    fun wakePersistsPacketResultWithoutChangingTargetStatus() = runTest {
        val repository = FakeRepository()
        val useCase = WakeDeviceUseCase(repository, object : WolSender {
            override suspend fun send(device: Device) = WolSendResult(WolResult.PACKET_SENT, 4, "packet_sent")
        })

        useCase(1)

        assertEquals(WolResult.PACKET_SENT, repository.device.lastWolResult)
        assertEquals(DeviceStatus.UNKNOWN, repository.device.status)
        assertNotNull(repository.device.lastWolAt)
    }

    private class FakeRepository : DeviceRepository {
        var device = Device(id = 1, name = "PC", ipAddress = "192.168.1.20", macAddress = "AA:BB:CC:DD:EE:FF")
        override fun observeDevices(): Flow<List<Device>> = MutableStateFlow(listOf(device))
        override fun observeDevice(id: Long): Flow<Device?> = MutableStateFlow(device.takeIf { it.id == id })
        override suspend fun getDevice(id: Long): Device? = device.takeIf { it.id == id }
        override suspend fun addDevice(device: Device): Long = device.id
        override suspend fun updateDevice(device: Device) { this.device = device }
        override suspend fun deleteDevice(id: Long) = Unit
    }
}
