package com.yihs.wolgateway.ui.screen.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yihs.wolgateway.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.ui.component.DeviceListItem
import com.yihs.wolgateway.ui.component.DeviceSearchBar
import com.yihs.wolgateway.ui.component.EmptyState
import com.yihs.wolgateway.ui.component.GatewayStatusStrip
import kotlinx.coroutines.delay

@Composable
fun DevicesScreen(
    onAdd: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onWake: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel(),
    settingsViewModel: com.yihs.wolgateway.ui.screen.settings.SettingsViewModel,
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val gatewayState by viewModel.gatewayState.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settingsState.batteryOptimizationIgnored) {
        if (!settingsState.batteryOptimizationIgnored) showBatteryDialog = true
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) settingsViewModel.refreshBatteryOptimizationStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val filteredDevices = remember(devices, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) devices else devices.filter {
            it.name.lowercase().contains(normalized) ||
                it.ipAddress.contains(normalized) ||
                it.macAddress.lowercase().contains(normalized)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            viewModel.devices.value.forEach { device -> viewModel.checkDeviceStatus(device.id) }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
            GatewayStatusStrip(
                serverState = gatewayState,
                tunnelState = tunnelState.status,
                modifier = Modifier.padding(top = 12.dp),
            )
            DeviceSearchBar(query, viewModel::setSearchQuery)
            if (devices.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.devices_empty_title),
                    message = stringResource(R.string.devices_empty_message),
                    actionLabel = stringResource(R.string.action_add_device),
                    onAction = onAdd,
                    modifier = Modifier.weight(1f),
                )
            } else if (filteredDevices.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.devices_no_match_title),
                    message = stringResource(R.string.devices_no_match_message),
                    actionLabel = stringResource(R.string.action_clear_search),
                    onAction = { viewModel.setSearchQuery("") },
                    modifier = Modifier.weight(1f),
                )
            } else {
                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refreshAllStatuses,
                    state = refreshState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredDevices, key = { it.id }) { device ->
                            DeviceListItem(
                                device = device,
                                onWake = { onWake(device.id) },
                                onOpen = { onOpenDevice(device.id) },
                            )
                        }
                    }
                }
            }
    }
    if (showBatteryDialog && !settingsState.batteryOptimizationIgnored) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.dialog_battery_title)) },
            text = { Text(stringResource(R.string.dialog_battery_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    settingsViewModel.requestBatteryOptimizationExemption()
                }) { Text(stringResource(R.string.action_review_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.action_later))
                }
            },
        )
    }
}
