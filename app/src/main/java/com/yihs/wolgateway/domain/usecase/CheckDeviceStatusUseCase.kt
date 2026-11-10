package com.yihs.wolgateway.domain.usecase

import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckResult
import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.service.StatusChecker
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val STATUS_CHECK_TIMEOUT_MS = 5_000L

class CheckDeviceStatusUseCase @Inject constructor(
    private val repository: DeviceRepository,
    private val checker: StatusChecker,
) {
    suspend operator fun invoke(deviceId: Long): StatusCheckResult {
        val device = repository.getDevice(deviceId) ?: throw DeviceNotFoundException(deviceId)
        val result = try {
            withTimeout(STATUS_CHECK_TIMEOUT_MS) {
                checker.check(device.ipAddress, device.statusCheckMethod, device.statusCheckPort)
            }
        } catch (_: TimeoutCancellationException) {
            StatusCheckResult(
                status = DeviceStatus.UNKNOWN,
                method = device.statusCheckMethod,
                latencyMs = STATUS_CHECK_TIMEOUT_MS,
                reason = "status_check_timeout",
            )
        }
        repository.updateDevice(device.copy(
            status = result.status,
            lastCheckAt = System.currentTimeMillis(),
            lastCheckLatencyMs = result.latencyMs,
            lastCheckReason = result.reason,
        ))
        return result
    }
}
