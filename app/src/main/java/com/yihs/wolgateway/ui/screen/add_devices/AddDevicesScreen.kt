package com.yihs.wolgateway.ui.screen.add_devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R
import com.yihs.wolgateway.network.discovery.DiscoveryResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDevicesScreen(
    onRegisterManually: () -> Unit,
    onRegisterDiscovered: (DiscoveryResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddDevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.scanIfNeeded() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val current = state) {
            AddDevicesState.NotStarted -> Unit
            AddDevicesState.Scanning -> ScanMessage(
                title = stringResource(R.string.add_devices_scanning),
                message = stringResource(R.string.add_devices_scanning_description),
                loading = true,
                modifier = Modifier.weight(1f),
            )
            AddDevicesState.Failed -> ScanMessage(
                title = stringResource(R.string.add_devices_scan_unavailable),
                message = stringResource(R.string.add_devices_scan_error),
                actionLabel = stringResource(R.string.action_retry_scan),
                onAction = viewModel::scan,
                modifier = Modifier.weight(1f),
            )
            is AddDevicesState.Complete -> CompleteScanContent(
                devices = current.devices,
                onRefresh = viewModel::scan,
                onRegisterDiscovered = onRegisterDiscovered,
                modifier = Modifier.weight(1f),
            )
        }
        Button(onClick = onRegisterManually, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_manually))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteScanContent(
    devices: List<DiscoveryResult>,
    onRefresh: () -> Unit,
    onRegisterDiscovered: (DiscoveryResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (devices.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ScanMessage(
                        title = stringResource(R.string.add_devices_nothing_found),
                        message = stringResource(R.string.add_devices_nothing_found_message),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.add_devices_reachable_devices),
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(devices, key = { it.address }) { device ->
                        ScanResultRow(
                            result = device,
                            onRegister = { onRegisterDiscovered(device) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) CircularProgressIndicator()
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ScanResultRow(result: DiscoveryResult, onRegister: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRegister),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(result.address, style = MaterialTheme.typography.titleMedium)
            Text(
                result.macAddress ?: stringResource(R.string.add_devices_mac_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (result.openPorts.isEmpty()) stringResource(R.string.add_devices_reachable_host)
                else stringResource(R.string.add_devices_open_tcp, result.openPorts.joinToString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
