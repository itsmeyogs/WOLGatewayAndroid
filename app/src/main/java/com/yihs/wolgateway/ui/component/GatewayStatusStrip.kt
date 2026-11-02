package com.yihs.wolgateway.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    rootMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.gateway_state), style = MaterialTheme.typography.titleSmall)
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
