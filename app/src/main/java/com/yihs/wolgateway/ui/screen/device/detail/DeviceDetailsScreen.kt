package com.yihs.wolgateway.ui.screen.device.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yihs.wolgateway.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.ui.component.DeviceStatusIndicator
import com.yihs.wolgateway.ui.screen.devices.DevicesViewModel

@Composable
fun DeviceDetailsScreen(
    deviceId: Long,
    onWake: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val device by viewModel.observeDevice(deviceId).collectAsStateWithLifecycle(initialValue = null)
    device?.let { current ->
        DeviceDetailsContent(
            device = current,
            onWake = onWake,
            onCheck = onCheck,
            modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        )
    }
}

@Composable
private fun DeviceDetailsContent(
    device: Device,
    onWake: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(device.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.details_saved_device),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DeviceStatusIndicator(device.status)
                }
                Spacer(Modifier.height(4.dp))
                Text(device.ipAddress, style = MaterialTheme.typography.bodyLarge)
                Text(
                    device.macAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (device.status != com.yihs.wolgateway.domain.model.DeviceStatus.ONLINE) {
                Button(onClick = onWake, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Text(stringResource(R.string.action_wake), modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(
                onClick = onCheck,
                modifier = if (device.status == com.yihs.wolgateway.domain.model.DeviceStatus.ONLINE) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.weight(1f)
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(stringResource(R.string.action_check_status), modifier = Modifier.padding(start = 6.dp))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.details_network_parameters), style = MaterialTheme.typography.titleMedium)
                ParameterGroupTitle(stringResource(R.string.details_wake_on_lan))
                DetailLine(stringResource(R.string.details_broadcast_address), device.broadcastAddress)
                DetailLine(stringResource(R.string.details_udp_port), device.wolPort.toString())
                ParameterGroupTitle(stringResource(R.string.details_availability_check))
                DetailLine(stringResource(R.string.details_method), device.statusCheckMethod.toDisplayLabel())
                DetailLine(stringResource(R.string.details_target_port), device.statusCheckPort.toString())
                DetailLine(
                    stringResource(R.string.details_latency),
                    device.lastCheckLatencyMs?.let { stringResource(R.string.details_latency_ms, it) }
                        ?: stringResource(R.string.details_not_available),
                )
                DetailLine(
                    stringResource(R.string.details_check_reason),
                    device.lastCheckReason ?: stringResource(R.string.details_not_available),
                )
            }
        }

    }
}

@Composable
private fun ParameterGroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun com.yihs.wolgateway.domain.model.StatusCheckMethod.toDisplayLabel(): String = when (this) {
    com.yihs.wolgateway.domain.model.StatusCheckMethod.ICMP_AND_TCP -> stringResource(R.string.status_method_icmp_tcp)
    com.yihs.wolgateway.domain.model.StatusCheckMethod.ICMP -> stringResource(R.string.status_method_icmp)
    com.yihs.wolgateway.domain.model.StatusCheckMethod.TCP -> stringResource(R.string.status_method_tcp)
}
