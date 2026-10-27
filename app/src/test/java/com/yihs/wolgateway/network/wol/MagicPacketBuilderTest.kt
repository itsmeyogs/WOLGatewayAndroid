package com.yihs.wolgateway.network.wol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MagicPacketBuilderTest {
    @Test
    fun builds102BytePacketWithSixPrefixBytesAndSixteenMacRepeats() {
        val packet = MagicPacketBuilder.build("AA:BB:CC:DD:EE:FF")

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        val mac = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        repeat(16) { index ->
            assertArrayEquals(mac, packet.copyOfRange(6 + index * 6, 12 + index * 6))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedMac() {
        MagicPacketBuilder.build("AA:BB:CC:DD:EE")
    }
}
