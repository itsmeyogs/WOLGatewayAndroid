package com.yihs.wolgateway.network.discovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import com.yihs.wolgateway.root.RootCommandExecutor
import com.yihs.wolgateway.root.RootSupervisor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject

class AndroidNetworkDiscovery @Inject constructor(
    private val rootExecutor: RootCommandExecutor,
    private val rootSupervisor: RootSupervisor,
    private val preferences: DataStore<Preferences>,
) : NetworkDiscovery {
    override suspend fun scan(): List<DiscoveryResult> = withContext(Dispatchers.IO) {
        withTimeout(SCAN_TIMEOUT_MS) {
            val subnet = findSubnet() ?: return@withTimeout emptyList()
            val rootMode = rootModeEnabled() && rootSupervisor.isRunning()
            val neighborMacs = if (rootMode) rootExecutor.neighborMacs() else emptyMap()
            val results = buildList {
                subnet.hosts().chunked(MAX_CONCURRENT_PROBES).forEach { addresses ->
                    coroutineContext.ensureActive()
                    addAll(coroutineScope {
                        addresses.map { address ->
                            async { probe(address, rootMode, neighborMacs) }
                        }.awaitAll().filterNotNull()
                    })
                }
            }
            val modeStillAllowsRoot = rootModeEnabled() && rootSupervisor.isRunning()
            DiscoveryResults.normalize(
                if (modeStillAllowsRoot) results else results.map { it.copy(macAddress = null) },
            )
        }
    }

    private suspend fun rootModeEnabled(): Boolean {
        val values = preferences.data.first()
        return values[GatewayStartupPreferences.ROOT_MODE_ENABLED] == true &&
            values[GatewayStartupPreferences.ROOT_MODE_CONSENTED] == true
    }

    private fun findSubnet(): Ipv4Subnet? = NetworkInterface.getNetworkInterfaces()
        ?.toList()
        .orEmpty()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .sortedBy(::interfacePriority)
        .flatMap { it.interfaceAddresses.asSequence() }
        .mapNotNull { interfaceAddress ->
            val address = interfaceAddress.address as? Inet4Address ?: return@mapNotNull null
            val prefixLength = interfaceAddress.networkPrefixLength.toInt()
            if (address.isLoopbackAddress || prefixLength !in 1..30) return@mapNotNull null
            Ipv4Subnet(address, prefixLength)
        }
        .firstOrNull()

    private fun interfacePriority(networkInterface: NetworkInterface): Int = when {
        networkInterface.name.startsWith("wlan") -> 0
        networkInterface.name.startsWith("eth") || networkInterface.name.startsWith("en") -> 1
        networkInterface.name.startsWith("rmnet") -> 2
        else -> 3
    }

    private suspend fun probe(
        address: InetAddress,
        rootMode: Boolean,
        neighborMacs: Map<String, String>,
    ): DiscoveryResult? = withTimeoutOrNull(HOST_TIMEOUT_MS) {
        val openPorts = coroutineScope {
            COMMON_PORTS.map { port -> async { port.takeIf { isOpen(address, it) } } }
                .awaitAll()
                .filterNotNull()
        }
        val reachable = openPorts.isNotEmpty() || address.isReachable(CONNECT_TIMEOUT_MS)
        if (!reachable) return@withTimeoutOrNull null
        val hostAddress = address.hostAddress ?: return@withTimeoutOrNull null
        val hostName = withTimeoutOrNull(HOSTNAME_TIMEOUT_MS) {
            address.hostName.takeUnless { it == hostAddress }
        }
        val macAddress = macForDiscovery(
            rootMode = rootMode && rootModeEnabled() && rootSupervisor.isRunning(),
            address = hostAddress,
            neighborMacs = neighborMacs,
            resolveNeighborMac = rootExecutor::resolveNeighborMac,
        )
        DiscoveryResult(hostAddress, hostName, openPorts, macAddress)
    }

    private fun isOpen(address: InetAddress, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
        }
        true
    }.getOrDefault(false)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 180
        const val HOST_TIMEOUT_MS = 700L
        const val HOSTNAME_TIMEOUT_MS = 250L
        const val SCAN_TIMEOUT_MS = 15_000L
        const val MAX_CONCURRENT_PROBES = 32
        val COMMON_PORTS = intArrayOf(22, 80, 443, 445, 3389)
    }
}

internal fun macForDiscovery(
    rootMode: Boolean,
    address: String,
    neighborMacs: Map<String, String>,
    resolveNeighborMac: (String) -> String?,
): String? = if (rootMode) {
    neighborMacs[address] ?: resolveNeighborMac(address)
} else {
    null
}
