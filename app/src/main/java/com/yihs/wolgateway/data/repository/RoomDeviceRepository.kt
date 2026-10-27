package com.yihs.wolgateway.data.repository

import com.yihs.wolgateway.data.local.DeviceDao
import com.yihs.wolgateway.data.local.entity.DeviceEntity
import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.repository.DuplicateDeviceException
import com.yihs.wolgateway.domain.repository.DuplicateDeviceField
import com.yihs.wolgateway.domain.validation.DeviceValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomDeviceRepository @Inject constructor(
    private val dao: DeviceDao,
) : DeviceRepository {
    override fun observeDevices(): Flow<List<Device>> = dao.observeAll().map { entities ->
        entities.map(DeviceEntity::toDomain)
    }

    override fun observeDevice(id: Long): Flow<Device?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getDevice(id: Long): Device? = dao.findById(id)?.toDomain()

    override suspend fun addDevice(device: Device): Long {
        val normalized = device.normalized()
        DeviceValidator.requireValid(normalized)
        ensureUnique(normalized)
        return dao.insert(normalized.toEntity())
    }

    override suspend fun updateDevice(device: Device) {
        require(device.id > 0) { "Device id must be positive" }
        val normalized = device.normalized()
        DeviceValidator.requireValid(normalized)
        if (dao.findById(normalized.id) == null) throw DeviceNotFoundException(normalized.id)
        ensureUnique(normalized)
        if (dao.update(normalized.toEntity()) != 1) throw DeviceNotFoundException(normalized.id)
    }

    override suspend fun deleteDevice(id: Long) {
        val existing = dao.findById(id) ?: throw DeviceNotFoundException(id)
        if (dao.delete(existing) != 1) throw DeviceNotFoundException(id)
    }

    private suspend fun ensureUnique(device: Device) {
        dao.findByMacAddress(device.macAddress)?.let {
            if (it.id != device.id) throw DuplicateDeviceException(DuplicateDeviceField.MAC_ADDRESS)
        }
    }
}

private fun Device.normalized(): Device = copy(
    name = name.trim(),
    ipAddress = ipAddress.trim(),
    macAddress = macAddress.trim().uppercase().replace('-', ':'),
    broadcastAddress = broadcastAddress.trim(),
)

private fun Device.toEntity() = DeviceEntity(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    broadcastAddress = broadcastAddress,
    wolPort = wolPort,
    statusCheckMethod = statusCheckMethod,
    statusCheckPort = statusCheckPort,
    status = status,
    lastCheckAt = lastCheckAt,
    lastCheckLatencyMs = lastCheckLatencyMs,
    lastCheckReason = lastCheckReason,
    lastWolResult = lastWolResult,
    lastWolAt = lastWolAt,
)

private fun DeviceEntity.toDomain() = Device(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    broadcastAddress = broadcastAddress,
    wolPort = wolPort,
    statusCheckMethod = statusCheckMethod,
    statusCheckPort = statusCheckPort,
    status = status,
    lastCheckAt = lastCheckAt,
    lastCheckLatencyMs = lastCheckLatencyMs,
    lastCheckReason = lastCheckReason,
    lastWolResult = lastWolResult,
    lastWolAt = lastWolAt,
)
