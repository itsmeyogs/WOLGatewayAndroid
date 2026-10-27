package com.yihs.wolgateway.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yihs.wolgateway.R
import com.yihs.wolgateway.domain.model.Device
import java.text.DateFormat
import java.util.Date

@Composable
fun DeviceListItem(
    device: Device,
    onWake: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceDescription = stringResource(R.string.device_content_description, device.name)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = deviceDescription },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                DeviceStatusIndicator(device.status)
            }
            Text(device.ipAddress, style = MaterialTheme.typography.bodyMedium)
            Text(device.macAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                    text = device.lastCheckAt?.let {
                        stringResource(
                            R.string.device_checked,
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)),
                        )
                    }
                    ?: stringResource(R.string.device_not_checked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onWake) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Text(stringResource(R.string.action_wake), modifier = Modifier.padding(start = 6.dp))
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.device_more_actions))
                }
            }
        }
    }
}
