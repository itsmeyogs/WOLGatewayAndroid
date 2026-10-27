package com.yihs.wolgateway.domain.usecase

import com.yihs.wolgateway.domain.model.StatusCheckResult
import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.service.StatusChecker
import javax.inject.Inject

class CheckDeviceStatusUseCase @Inject constructor(
    private val repository: DeviceRepository,
    private val checker: StatusChecker,
) {
    suspend operator fun invoke(deviceId: Long): StatusCheckResult {
        val device = repository.getDevice(deviceId) ?: throw DeviceNotFoundException(deviceId)
        val result = checker.check(device.ipAddress, device.statusCheckMethod, device.statusCheckPort)
        repository.updateDevice(device.copy(
            status = result.status,
            lastCheckAt = System.currentTimeMillis(),
            lastCheckLatencyMs = result.latencyMs,
            lastCheckReason = result.reason,
        ))
        return result
    }
}
