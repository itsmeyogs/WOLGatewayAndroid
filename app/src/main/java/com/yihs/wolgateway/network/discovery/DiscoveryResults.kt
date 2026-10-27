package com.yihs.wolgateway.network.discovery

import com.yihs.wolgateway.domain.model.Device

object DiscoveryResults {
    fun excludeSavedAddresses(
        results: Iterable<DiscoveryResult>,
        savedDevices: Iterable<Device>,
    ): List<DiscoveryResult> {
        val savedAddresses = savedDevices.map { it.ipAddress }.toSet()
        return results.filterNot { it.address in savedAddresses }
    }

    fun normalize(results: Iterable<DiscoveryResult>): List<DiscoveryResult> = results
        .groupBy { it.address }
        .values
        .map { candidates ->
            candidates.first().copy(
                hostName = candidates.firstNotNullOfOrNull { it.hostName },
                openPorts = candidates.flatMap { it.openPorts }.distinct().sorted(),
            )
        }
        .sortedBy { it.address.toIpSortKey() }

    private fun String.toIpSortKey(): Long = split('.')
        .mapNotNull(String::toLongOrNull)
        .fold(0L) { result, octet -> (result shl 8) + octet }
}
