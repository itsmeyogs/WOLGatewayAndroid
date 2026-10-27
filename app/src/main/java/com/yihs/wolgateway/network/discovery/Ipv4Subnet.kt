package com.yihs.wolgateway.network.discovery

import java.net.Inet4Address
import java.net.InetAddress

internal data class Ipv4Subnet(
    private val localAddress: Inet4Address,
    private val prefixLength: Int,
) {
    fun hosts(): Sequence<InetAddress> {
        require(prefixLength in 1..30)
        val local = localAddress.address.toUnsignedInt()
        val mask = -1 shl (32 - prefixLength)
        val first = (local and mask) + 1
        val last = (local or mask.inv()) - 1
        return (first..last).asSequence()
            .filter { it != local }
            .map(Int::toIpv4Address)
    }
}

private fun ByteArray.toUnsignedInt(): Int = fold(0) { result, byte ->
    (result shl 8) or (byte.toInt() and 0xff)
}

private fun Int.toIpv4Address(): InetAddress = InetAddress.getByAddress(
    byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        toByte(),
    ),
)
