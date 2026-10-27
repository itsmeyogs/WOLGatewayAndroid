package com.yihs.wolgateway.network.status

import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.StatusCheckResult
import com.yihs.wolgateway.domain.service.StatusChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class AndroidStatusChecker @Inject constructor() : StatusChecker {
    override suspend fun check(
        ipAddress: String,
        method: StatusCheckMethod,
        port: Int,
    ): StatusCheckResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        when (method) {
            StatusCheckMethod.ICMP -> checkIcmp(ipAddress)
            StatusCheckMethod.TCP -> checkTcp(ipAddress, port)
            StatusCheckMethod.ICMP_AND_TCP -> combine(
                checkIcmp(ipAddress),
                checkTcp(ipAddress, port),
            )
        }
    }

    private fun checkIcmp(ipAddress: String): StatusCheckResult {
        val startedAt = System.nanoTime()
        return try {
            val reachable = InetAddress.getByName(ipAddress).isReachable(ICMP_TIMEOUT_MS)
            if (reachable) {
                result(DeviceStatus.ONLINE, StatusCheckMethod.ICMP, startedAt, "icmp_reply")
            } else {
                result(DeviceStatus.UNKNOWN, StatusCheckMethod.ICMP, startedAt, "icmp_no_reply")
            }
        } catch (_: UnknownHostException) {
            result(DeviceStatus.OFFLINE, StatusCheckMethod.ICMP, startedAt, "host_not_found")
        } catch (_: Exception) {
            result(DeviceStatus.UNKNOWN, StatusCheckMethod.ICMP, startedAt, "icmp_unavailable")
        }
    }

    private fun checkTcp(ipAddress: String, port: Int): StatusCheckResult {
        val startedAt = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ipAddress, port), TCP_TIMEOUT_MS)
            }
            result(DeviceStatus.ONLINE, StatusCheckMethod.TCP, startedAt, "tcp_connected")
        } catch (_: UnknownHostException) {
            result(DeviceStatus.OFFLINE, StatusCheckMethod.TCP, startedAt, "host_not_found")
        } catch (_: NoRouteToHostException) {
            result(DeviceStatus.OFFLINE, StatusCheckMethod.TCP, startedAt, "no_route_to_host")
        } catch (_: SocketTimeoutException) {
            result(DeviceStatus.UNKNOWN, StatusCheckMethod.TCP, startedAt, "tcp_timeout")
        } catch (_: ConnectException) {
            result(DeviceStatus.UNKNOWN, StatusCheckMethod.TCP, startedAt, "tcp_connection_refused")
        } catch (_: Exception) {
            result(DeviceStatus.UNKNOWN, StatusCheckMethod.TCP, startedAt, "tcp_unavailable")
        }
    }

    private fun combine(icmp: StatusCheckResult, tcp: StatusCheckResult): StatusCheckResult {
        val status = when {
            icmp.status == DeviceStatus.ONLINE || tcp.status == DeviceStatus.ONLINE -> DeviceStatus.ONLINE
            icmp.status == DeviceStatus.OFFLINE && tcp.status == DeviceStatus.OFFLINE -> DeviceStatus.OFFLINE
            else -> DeviceStatus.UNKNOWN
        }
        return StatusCheckResult(
            status = status,
            method = StatusCheckMethod.ICMP_AND_TCP,
            latencyMs = listOfNotNull(icmp.latencyMs, tcp.latencyMs).minOrNull(),
            reason = "icmp=${icmp.reason}; tcp=${tcp.reason}",
        )
    }

    private fun result(
        status: DeviceStatus,
        method: StatusCheckMethod,
        startedAt: Long,
        reason: String,
    ) = StatusCheckResult(status, method, (System.nanoTime() - startedAt) / 1_000_000, reason)

    private companion object {
        const val ICMP_TIMEOUT_MS = 1_000
        const val TCP_TIMEOUT_MS = 1_000
    }
}
