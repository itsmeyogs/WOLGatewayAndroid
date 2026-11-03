package com.yihs.wolgateway.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yihs.wolgateway.data.local.GatewayDatabase
import com.yihs.wolgateway.data.repository.RoomDeviceRepository
import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.repository.DuplicateDeviceException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRepositoryTest {
    private lateinit var database: GatewayDatabase
    private lateinit var repository: RoomDeviceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java).build()
        repository = RoomDeviceRepository(database.deviceDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun crudAndFlowStayConsistent() = runBlocking {
        val id = repository.addDevice(device())
        assertEquals(listOf("Office PC"), repository.observeDevices().first().map { it.name })

        repository.updateDevice(device(id).copy(name = "Updated PC"))
        assertEquals("Updated PC", repository.getDevice(id)?.name)

        repository.deleteDevice(id)
        assertEquals(emptyList<Device>(), repository.observeDevices().first())
    }

    @Test
    fun duplicateIpWithDifferentMacIsAllowed() {
        runBlocking {
            repository.addDevice(device())

            repository.addDevice(device().copy(name = "Second PC", macAddress = "11:22:33:44:55:66"))
            assertEquals(2, repository.observeDevices().first().size)
        }
    }

    @Test
    fun duplicateMacIsRejected() {
        runBlocking {
            repository.addDevice(device())

            assertThrows(DuplicateDeviceException::class.java) {
                runBlocking { repository.addDevice(device().copy(name = "Second PC", ipAddress = "192.168.1.21")) }
            }
        }
    }

    private fun device(id: Long = 0) = Device(
        id = id,
        name = "Office PC",
        ipAddress = "192.168.1.20",
        macAddress = "AA:BB:CC:DD:EE:FF",
    )
}
