package com.yihs.wolgateway.ui.screen.tunnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    onCopyText: (String) -> Unit,
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
    val canDisconnect = state.status == TunnelState.CONNECTED || state.status == TunnelState.CONNECTING || state.status == TunnelState.RECONNECTING
    val canConnect = state.status == TunnelState.DISCONNECTED || state.status == TunnelState.ERROR || state.status == TunnelState.NOT_CONFIGURED

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tunnel_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tunnel_connection), style = MaterialTheme.typography.titleMedium)
                Text(statusText, style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = state.originAddress,
                    onValueChange = viewModel::onOriginChanged,
                    label = { Text(stringResource(R.string.tunnel_origin)) },
                    supportingText = { Text(stringResource(R.string.tunnel_origin_help)) },
                    isError = state.originError,
                    singleLine = true,
                    enabled = canConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (canConnect) Button(onClick = viewModel::connect) { Text(stringResource(R.string.action_connect_tunnel)) }
                    if (canDisconnect) TextButton(onClick = viewModel::disconnect) { Text(stringResource(R.string.action_disconnect_tunnel)) }
                    if (state.status == TunnelState.ERROR || state.status == TunnelState.CONNECTED) {
                        TextButton(onClick = viewModel::reconnect) { Text(stringResource(R.string.action_reconnect_tunnel)) }
                    }
                }
                state.error?.let { error ->
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tunnel_token), style = MaterialTheme.typography.titleMedium)
                if (state.isEditingToken) {
                    OutlinedTextField(
                        value = state.tokenInput,
                        onValueChange = viewModel::onTokenInputChanged,
                        label = { Text(stringResource(R.string.tunnel_token_input)) },
                        supportingText = { Text(stringResource(state.tokenError ?: R.string.tunnel_token_help)) },
                        isError = state.tokenError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = viewModel::saveToken) { Text(stringResource(R.string.action_save_tunnel_token)) }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.tokenPreview, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onCopyText(state.tokenPreview) }) {
                            Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy_tunnel_token))
                        }
                        IconButton(onClick = viewModel::editToken) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit_tunnel_token))
                        }
                        IconButton(onClick = viewModel::editToken) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.action_replace_tunnel_token))
                        }
                    }
                }
            }
        }
    }
}
