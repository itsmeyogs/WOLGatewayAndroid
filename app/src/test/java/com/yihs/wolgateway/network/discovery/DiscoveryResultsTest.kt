package com.yihs.wolgateway.network.discovery

import com.yihs.wolgateway.domain.model.Device
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryResultsTest {
    @Test
    fun standardModeNeverResolvesMacThroughRootExecutor() {
        var resolverCalls = 0
        assertEquals(
            null,
            macForDiscovery(
                rootMode = false,
                address = "192.168.1.20",
                neighborMacs = mapOf("192.168.1.20" to "AA:BB:CC:DD:EE:FF"),
                resolveNeighborMac = {
                    resolverCalls++
                    "AA:BB:CC:DD:EE:FF"
                },
            ),
        )
        assertEquals(0, resolverCalls)
    }

    @Test
    fun rootModeResolvesMacFromNeighborDataOrExecutor() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            macForDiscovery(
                rootMode = true,
                address = "192.168.1.20",
                neighborMacs = mapOf("192.168.1.20" to "AA:BB:CC:DD:EE:FF"),
                resolveNeighborMac = { "11:22:33:44:55:66" },
            ),
        )
        assertEquals(
            "11:22:33:44:55:66",
            macForDiscovery(
                rootMode = true,
                address = "192.168.1.21",
                neighborMacs = emptyMap(),
                resolveNeighborMac = { "11:22:33:44:55:66" },
            ),
        )
    }

    @Test
    fun savedIpIsHiddenFromDiscoveryResults() {
        val visible = DiscoveryResults.excludeSavedAddresses(
            results = listOf(DiscoveryResult("192.168.1.20"), DiscoveryResult("192.168.1.21")),
            savedDevices = listOf(
                Device(name = "Office", ipAddress = "192.168.1.20", macAddress = "AA:BB:CC:DD:EE:FF"),
            ),
        )

        assertEquals(listOf("192.168.1.21"), visible.map { it.address })
    }

    @Test
    fun normalizeDeduplicatesMergesPortsAndSortsAddresses() {
        val results = DiscoveryResults.normalize(
            listOf(
                DiscoveryResult("192.168.1.20", openPorts = listOf(3389)),
                DiscoveryResult("192.168.1.2", hostName = "media", openPorts = listOf(80)),
                DiscoveryResult("192.168.1.20", hostName = "office", openPorts = listOf(22)),
            ),
        )

        assertEquals(listOf("192.168.1.2", "192.168.1.20"), results.map { it.address })
        assertEquals("office", results[1].hostName)
        assertEquals(listOf(22, 3389), results[1].openPorts)
    }

    @Test
    fun subnetExcludesNetworkBroadcastAndLocalAddress() {
        val local = InetAddress.getByName("192.168.1.10") as Inet4Address
        val hosts = Ipv4Subnet(local, 29).hosts().map { it.hostAddress }.toList()

        assertEquals(
            listOf("192.168.1.9", "192.168.1.11", "192.168.1.12", "192.168.1.13", "192.168.1.14"),
            hosts,
        )
        assertTrue("192.168.1.8" !in hosts)
        assertTrue("192.168.1.15" !in hosts)
    }
}
