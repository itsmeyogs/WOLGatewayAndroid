package com.yihs.wolgateway.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yihs.wolgateway.R
import com.yihs.wolgateway.gateway.server.ServerState
import com.yihs.wolgateway.tunnel.model.TunnelState

@Composable
fun GatewayStatusStrip(
    serverState: ServerState,
    tunnelState: TunnelState,
    modifier: Modifier = Modifier,
    rootMode: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.gateway_state), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GatewayStatus(
                    stringResource(R.string.gateway_api),
                    stringResource(
                        if (serverState == ServerState.RUNNING) R.string.gateway_api_running
                        else R.string.gateway_api_stopped,
                    ),
                )
                GatewayStatus(
                    stringResource(R.string.gateway_tunnel),
                    stringResource(tunnelStatusResource(tunnelState)),
                )
                GatewayStatus(
                    stringResource(R.string.gateway_service),
                    stringResource(if (rootMode) R.string.gateway_service_root else R.string.gateway_service_standard),
                )
            }
        }
    }
}

private fun tunnelStatusResource(state: TunnelState): Int = when (state) {
    TunnelState.NOT_CONFIGURED -> R.string.tunnel_status_not_configured
    TunnelState.DISCONNECTED -> R.string.tunnel_status_disconnected
    TunnelState.CONNECTING -> R.string.tunnel_status_connecting
    TunnelState.CONNECTED -> R.string.tunnel_status_connected
    TunnelState.RECONNECTING -> R.string.tunnel_status_reconnecting
    TunnelState.ERROR -> R.string.tunnel_status_error
}

@Composable
private fun GatewayStatus(label: String, value: String) {
    val icon = when (label) {
        stringResource(R.string.gateway_api) -> Icons.Filled.Dns
        stringResource(R.string.gateway_tunnel) -> Icons.Filled.Cloud
        else -> Icons.Filled.SettingsSuggest
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
