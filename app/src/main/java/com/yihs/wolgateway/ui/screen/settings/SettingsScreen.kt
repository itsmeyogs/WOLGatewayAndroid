package com.yihs.wolgateway.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R

@Composable
fun SettingsScreen(
    onOpenApi: () -> Unit,
    onOpenTunnel: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.gateway_configuration),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenApi),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.settings_api), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_api_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTunnel),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_tunnel), style = MaterialTheme.typography.titleMedium)
                 Text(stringResource(R.string.tunnel_description_short), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AutostartRow(
            title = stringResource(R.string.settings_auto_start_api),
            description = stringResource(R.string.settings_auto_start_api_description),
            checked = state.autoStartApi,
            onCheckedChange = viewModel::setAutoStartApi,
        )
        AutostartRow(
            title = stringResource(R.string.settings_auto_start_tunnel),
            description = stringResource(R.string.settings_auto_start_tunnel_description),
            checked = state.autoStartTunnel,
            enabled = state.tunnelTokenConfigured,
            onCheckedChange = viewModel::setAutoStartTunnel,
        )
        if (!state.tunnelTokenConfigured) {
            Text(
                stringResource(R.string.settings_auto_start_tunnel_token_required),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            state.tunnelErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ?: state.tunnelError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
        SettingRow(stringResource(R.string.settings_background), stringResource(R.string.settings_background_value))
        SettingRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_value))
    }
}

@Composable
private fun AutostartRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
