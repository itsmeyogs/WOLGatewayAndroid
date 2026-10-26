package com.yihs.wolgateway.domain.repository

import com.yihs.wolgateway.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevices(): Flow<List<Device>>

    fun observeDevice(id: Long): Flow<Device?>

    suspend fun getDevice(id: Long): Device?

    suspend fun addDevice(device: Device): Long

    suspend fun updateDevice(device: Device)

    suspend fun deleteDevice(id: Long)
}
