package com.yihs.wolgateway.ui.screen.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R
import com.yihs.wolgateway.diagnostics.DiagnosticsCopyFormatter
import com.yihs.wolgateway.diagnostics.DiagnosticsSnapshot
import com.yihs.wolgateway.ui.component.DiagnosticItemRow

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = viewModel.snapshot.collectAsStateWithLifecycle().value
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snapshot == null) {
            Text(stringResource(R.string.diagnostics_loading))
        } else {
            DiagnosticsContent(snapshot, onCopy)
        }
    }
}

@Composable
private fun DiagnosticsContent(snapshot: DiagnosticsSnapshot, onCopy: (String) -> Unit) {
    Section(stringResource(R.string.diagnostics_general)) {
        listOf(
            stringResource(R.string.diagnostics_app_version) to snapshot.appVersion,
            stringResource(R.string.diagnostics_android) to snapshot.androidVersion,
            stringResource(R.string.diagnostics_abi) to snapshot.abi,
            stringResource(R.string.diagnostics_device) to snapshot.deviceClassification.name.lowercase().replace('_', ' '),
            stringResource(R.string.diagnostics_network) to snapshot.networkInterface,
        ).forEach { Text("${it.first}: ${it.second}") }
        DiagnosticItemRow(snapshot.api)
        DiagnosticItemRow(snapshot.tunnel)
        DiagnosticItemRow(snapshot.gatewayService)
        if (snapshot.safeErrors.isNotEmpty()) {
            Text(stringResource(R.string.diagnostics_last_errors), style = MaterialTheme.typography.titleSmall)
            snapshot.safeErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    Section(if (snapshot.mode.name == "ROOT") stringResource(R.string.diagnostics_root_mode) else stringResource(R.string.diagnostics_standard_mode)) {
        (if (snapshot.mode.name == "ROOT") snapshot.root else snapshot.standard).forEach { DiagnosticItemRow(it) }
    }
    OutlinedButton(onClick = { onCopy(DiagnosticsCopyFormatter.format(snapshot)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_copy_diagnostics))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()
        content()
    }
}
