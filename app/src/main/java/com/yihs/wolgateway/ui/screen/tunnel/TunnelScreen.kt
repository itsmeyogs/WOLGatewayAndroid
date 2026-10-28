package com.yihs.wolgateway.ui.screen.tunnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R
import com.yihs.wolgateway.tunnel.model.TunnelState

@Composable
fun TunnelScreen(
    viewModel: TunnelViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusText = stringResource(
        when (state.status) {
            TunnelState.NOT_CONFIGURED -> R.string.tunnel_status_not_configured
            TunnelState.DISCONNECTED -> R.string.tunnel_status_disconnected
            TunnelState.CONNECTING -> R.string.tunnel_status_connecting
            TunnelState.CONNECTED -> R.string.tunnel_status_connected
            TunnelState.RECONNECTING -> R.string.tunnel_status_reconnecting
            TunnelState.ERROR -> R.string.tunnel_status_error
        },
    )
    val connectionEnabled = state.status != TunnelState.NOT_CONFIGURED
    val connectionChecked = state.status == TunnelState.CONNECTING ||
        state.status == TunnelState.CONNECTED ||
        state.status == TunnelState.RECONNECTING
    val showTokenForm = !state.tokenConfigured || state.isEditingToken

    DisposableEffect(Unit) {
        onDispose(viewModel::resetTokenEdit)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.tunnel_connection), style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = connectionChecked || state.autoStartTunnel,
                            onCheckedChange = { enabled -> if (enabled) viewModel.connect() else viewModel.disconnect() },
                            enabled = connectionEnabled,
                        )
                    }
                    Text(statusText, style = MaterialTheme.typography.bodySmall)
                    val connectionMessage = when {
                        !state.tokenConfigured -> stringResource(R.string.tunnel_token_required)
                        state.errorMessage != null -> state.errorMessage
                        state.error != null -> state.error?.let { stringResource(it) }
                        else -> null
                    }
                    connectionMessage?.let { errorMessage ->
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (connectionChecked) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.tunnel_auto_reconnect), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.tunnel_auto_reconnect_help),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = state.autoReconnect, onCheckedChange = viewModel::setAutoReconnect)
                    }
                }
                HorizontalDivider()
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tunnel_token), style = MaterialTheme.typography.titleMedium)
                    if (showTokenForm) {
                        OutlinedTextField(
                            value = state.tokenInput,
                            onValueChange = viewModel::onTokenInputChanged,
                            label = { Text(stringResource(R.string.tunnel_token_input)) },
                            supportingText = {
                                Text(
                                    stringResource(state.tokenError ?: R.string.tunnel_token_help),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            isError = state.tokenError != null,
                            singleLine = true,
                            enabled = !connectionChecked,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::saveToken, enabled = !connectionChecked) {
                                Text(stringResource(R.string.action_save_token))
                            }
                            if (state.tokenConfigured) {
                                TextButton(
                                    onClick = viewModel::cancelTokenEdit,
                                    enabled = !connectionChecked,
                                ) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(state.tokenPreview, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (!connectionChecked) IconButton(onClick = viewModel::editToken) {
                                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit_token))
                            }
                        }
                    }
                }
            }
        }
    }
}
