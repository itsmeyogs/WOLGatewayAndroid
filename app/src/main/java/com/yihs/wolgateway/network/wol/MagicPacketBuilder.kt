package com.yihs.wolgateway.network.wol

object MagicPacketBuilder {
    private val macPattern = Regex("^[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}$")

    fun build(macAddress: String): ByteArray {
        require(macPattern.matches(macAddress.trim())) { "Invalid MAC address" }
        val mac = macAddress.trim().replace('-', ':').split(':').map { it.toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * mac.size) { index -> mac[index % mac.size] }
    }
}
