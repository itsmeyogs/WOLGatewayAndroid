package com.yihs.wolgateway.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yihs.wolgateway.R
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.ui.theme.rustStatus
import com.yihs.wolgateway.ui.theme.slateStatus
import com.yihs.wolgateway.ui.theme.tealStatus

@Composable
fun DeviceStatusIndicator(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (icon, color, label) = when (status) {
        DeviceStatus.ONLINE -> Triple(Icons.Filled.CheckCircle, tealStatus, stringResource(R.string.status_online))
        DeviceStatus.OFFLINE -> Triple(Icons.Filled.Warning, rustStatus, stringResource(R.string.status_offline))
        DeviceStatus.UNKNOWN -> Triple(Icons.AutoMirrored.Filled.HelpOutline, slateStatus, stringResource(R.string.status_unknown))
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
