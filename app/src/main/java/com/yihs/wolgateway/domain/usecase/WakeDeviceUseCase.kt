package com.yihs.wolgateway.domain.usecase

import com.yihs.wolgateway.domain.model.WolSendResult
import com.yihs.wolgateway.domain.repository.DeviceNotFoundException
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.service.WolSender
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import javax.inject.Inject

class WakeDeviceUseCase @Inject constructor(
    private val repository: DeviceRepository,
    private val sender: WolSender,
) {
    suspend operator fun invoke(deviceId: Long): WolSendResult {
        val device = repository.getDevice(deviceId) ?: throw DeviceNotFoundException(deviceId)
        coroutineContext.ensureActive()
        val result = sender.send(device)
        repository.updateDevice(device.copy(
            lastWolResult = result.result,
            lastWolAt = System.currentTimeMillis(),
        ))
        return result
    }
}
