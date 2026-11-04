package com.yihs.wolgateway.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yihs.wolgateway.diagnostics.DiagnosticItem
import com.yihs.wolgateway.diagnostics.DiagnosticStatus
import com.yihs.wolgateway.R
import androidx.compose.ui.res.stringResource

@Composable
fun DiagnosticItemRow(item: DiagnosticItem, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(item.value, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(item.status.resourceId()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DiagnosticStatus.resourceId() = when (this) {
    DiagnosticStatus.PASS -> R.string.diagnostics_status_pass
    DiagnosticStatus.WARN -> R.string.diagnostics_status_warn
    DiagnosticStatus.FAIL -> R.string.diagnostics_status_fail
    DiagnosticStatus.NOT_CONFIGURED -> R.string.diagnostics_status_not_configured
}
