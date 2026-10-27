package com.yihs.wolgateway.domain.service

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.model.WolSendResult

interface WolSender {
    suspend fun send(device: Device): WolSendResult
}
