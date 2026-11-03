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

@Composable
fun DiagnosticItemRow(item: DiagnosticItem, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(item.value, style = MaterialTheme.typography.bodyMedium)
            Text(item.status.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DiagnosticStatus.label() = name.lowercase().replace('_', ' ')
