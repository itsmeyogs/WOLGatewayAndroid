package com.yihs.wolgateway.network.wol

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.model.WolResult
import com.yihs.wolgateway.domain.model.WolSendResult
import com.yihs.wolgateway.domain.service.WolSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class UdpWolSender @Inject constructor() : WolSender {
    override suspend fun send(device: Device): WolSendResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
            coroutineContext.ensureActive()
            val payload = MagicPacketBuilder.build(device.macAddress)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val destination = InetAddress.getByName(device.broadcastAddress)
                socket.send(DatagramPacket(payload, payload.size, destination, device.wolPort))
            }
            WolSendResult(WolResult.PACKET_SENT, elapsedMs(startedAt), "packet_sent")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            WolSendResult(WolResult.FAILED, elapsedMs(startedAt), "packet_send_failed")
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
