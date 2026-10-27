package com.yihs.wolgateway.ui.screen.add_devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yihs.wolgateway.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

private sealed interface ScanState {
    data object Scanning : ScanState
    data class Complete(val devices: List<ScanResult>) : ScanState
    data class Failed(val message: String) : ScanState
}

private data class ScanResult(
    val address: String,
    val hostName: String?,
    val ports: List<Int>,
)

@Composable
fun AddDevicesScreen(onRegisterManually: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    val scanErrorMessage = stringResource(R.string.add_devices_scan_error)

    LaunchedEffect(Unit) {
        state = runCatching { NetworkScanner.scan() }
            .fold(
                onSuccess = { ScanState.Complete(it) },
                onFailure = { ScanState.Failed(scanErrorMessage) },
            )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val current = state) {
            ScanState.Scanning -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.add_devices_scanning))
                        Text(stringResource(R.string.add_devices_scanning_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is ScanState.Failed -> ScanMessage(
                title = stringResource(R.string.add_devices_scan_unavailable),
                message = current.message,
                modifier = Modifier.weight(1f),
            )
            is ScanState.Complete -> {
                Text(
                    if (current.devices.isEmpty()) stringResource(R.string.add_devices_no_reachable_devices)
                    else stringResource(R.string.add_devices_reachable_devices),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (current.devices.isEmpty()) {
                    ScanMessage(
                        title = stringResource(R.string.add_devices_nothing_found),
                        message = stringResource(R.string.add_devices_nothing_found_message),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(current.devices, key = { it.address }) { device -> ScanResultRow(device) }
                    }
                }
            }
        }
        FilledTonalButton(onClick = onRegisterManually, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_manually))
        }
    }
}

@Composable
private fun ScanMessage(title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScanResultRow(result: ScanResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.hostName ?: stringResource(R.string.add_devices_unknown_device), style = MaterialTheme.typography.titleMedium)
            Text(result.address, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (result.ports.isEmpty()) stringResource(R.string.add_devices_reachable_host)
                    else stringResource(R.string.add_devices_open_tcp, result.ports.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private object NetworkScanner {
    private const val CONNECT_TIMEOUT_MS = 180
    private val commonPorts = intArrayOf(22, 80, 443, 445, 3389)

    suspend fun scan(): List<ScanResult> = withContext(Dispatchers.IO) {
        val subnet = findSubnet() ?: throw IllegalStateException()
        coroutineScope {
            subnet.hosts().map { address -> async { probe(address) } }.awaitAll()
                .filterNotNull()
                .sortedBy { it.address }
        }
    }

    private fun findSubnet(): Subnet? = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.interfaceAddresses.asSequence() }
        .mapNotNull { address ->
            val ipv4 = address.address as? Inet4Address ?: return@mapNotNull null
            if (ipv4.isLoopbackAddress || address.networkPrefixLength !in 1..30) return@mapNotNull null
            Subnet(ipv4, address.networkPrefixLength.toInt())
        }
        .firstOrNull()

    private fun probe(address: InetAddress): ScanResult? {
        val openPorts = commonPorts.filter { port -> isOpen(address, port) }
        if (openPorts.isEmpty() && !address.isReachable(CONNECT_TIMEOUT_MS)) return null
        val hostAddress = address.hostAddress ?: return null
        val hostName = address.hostName.takeUnless { it == hostAddress }
        return ScanResult(hostAddress, hostName, openPorts)
    }

    private fun isOpen(address: InetAddress, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS) }
        true
    }.getOrDefault(false)
}

private data class Subnet(private val address: Inet4Address, private val prefixLength: Int) {
    fun hosts(): List<InetAddress> {
        val local = address.address.toUnsignedInt()
        val mask = -1 shl (32 - prefixLength)
        val first = (local and mask) + 1
        val last = (local or mask.inv()) - 1
        return (first..last).map { it.toIpv4Address() }
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
