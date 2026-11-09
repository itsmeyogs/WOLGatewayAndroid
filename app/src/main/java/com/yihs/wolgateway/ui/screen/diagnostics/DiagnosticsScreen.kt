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
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R
import com.yihs.wolgateway.diagnostics.DiagnosticsCopyFormatter
import com.yihs.wolgateway.diagnostics.DiagnosticsCopyLabels
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
    DiagnosticsCard(stringResource(R.string.diagnostics_general)) {
        listOf(
            stringResource(R.string.diagnostics_app_version) to snapshot.appVersion,
            stringResource(R.string.diagnostics_android) to snapshot.androidVersion,
            stringResource(R.string.diagnostics_abi) to snapshot.abi,
             stringResource(R.string.diagnostics_device) to snapshot.deviceClassification?.let {
                 stringResource(
                 when (it) {
                      com.yihs.wolgateway.diagnostics.DeviceClassification.AUTOMATIC_TEST -> R.string.diagnostics_classification_automatic_test
                      com.yihs.wolgateway.diagnostics.DeviceClassification.EMULATOR -> R.string.diagnostics_classification_emulator
                      com.yihs.wolgateway.diagnostics.DeviceClassification.OTHER -> R.string.diagnostics_device
                  },
               )
             },
            stringResource(R.string.diagnostics_manufacturer) to snapshot.deviceInfo.manufacturer,
            stringResource(R.string.diagnostics_model) to snapshot.deviceInfo.model,
            stringResource(R.string.diagnostics_product) to snapshot.deviceInfo.product,
            stringResource(R.string.diagnostics_network) to snapshot.networkInterface,
         ).forEach { Text("${it.first}: ${it.second}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
         Text("${stringResource(R.string.diagnostics_battery_level)}: ${snapshot.batteryInfo.levelPercent?.let { "$it%" } ?: stringResource(R.string.diagnostics_unknown)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
         Text("${stringResource(R.string.diagnostics_charging)}: ${snapshot.batteryInfo.charging ?: stringResource(R.string.diagnostics_unknown)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
          Text("${stringResource(R.string.diagnostics_battery_saver)}: ${snapshot.batteryInfo.batterySaver ?: stringResource(R.string.diagnostics_unknown)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
         DiagnosticItemRow(snapshot.api)
        DiagnosticItemRow(snapshot.tunnel)
        DiagnosticItemRow(snapshot.gatewayService)
        if (snapshot.safeErrors.isNotEmpty()) {
            Text(stringResource(R.string.diagnostics_last_errors), style = MaterialTheme.typography.titleSmall)
            snapshot.safeErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    DiagnosticsCard(stringResource(R.string.diagnostics_thermal)) {
        Text("${stringResource(R.string.diagnostics_thermal_status)}: ${snapshot.thermalInfo.status}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text("${stringResource(R.string.diagnostics_thermal_source)}: ${snapshot.thermalInfo.source}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text("${stringResource(R.string.diagnostics_thermal_battery)}: ${snapshot.thermalInfo.batteryTemperatureCelsius?.let { "%.1f °C".format(it) } ?: stringResource(R.string.diagnostics_unknown)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text("${stringResource(R.string.diagnostics_thermal_headroom)}: ${snapshot.thermalInfo.thermalHeadroom ?: stringResource(R.string.diagnostics_unknown)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        snapshot.thermalInfo.sensors.forEach { sensor ->
            Text("${sensor.name}: %.1f °C (${sensor.status})".format(sensor.temperatureCelsius), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        snapshot.thermalInfo.safeError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    DiagnosticsCard(if (snapshot.mode.name == "ROOT") stringResource(R.string.diagnostics_root_mode) else stringResource(R.string.diagnostics_standard_mode)) {
        (if (snapshot.mode.name == "ROOT") snapshot.root else snapshot.standard).forEach { DiagnosticItemRow(it) }
    }
    val labels = DiagnosticsCopyLabels(
        header = stringResource(R.string.diagnostics_header),
        appVersion = stringResource(R.string.diagnostics_app_version),
        android = stringResource(R.string.diagnostics_android),
        abi = stringResource(R.string.diagnostics_abi),
        device = stringResource(R.string.diagnostics_device),
        network = stringResource(R.string.diagnostics_network),
        api = stringResource(R.string.diagnostics_api),
        tunnel = stringResource(R.string.diagnostics_tunnel),
        gatewayService = stringResource(R.string.diagnostics_gateway_service),
        rootMode = stringResource(R.string.diagnostics_root_mode),
        standardMode = stringResource(R.string.diagnostics_standard_mode),
        errors = stringResource(R.string.diagnostics_errors),
        none = stringResource(R.string.diagnostics_none),
        sensitiveRedacted = stringResource(R.string.diagnostics_redacted),
         automaticTest = stringResource(R.string.diagnostics_classification_automatic_test),
         emulator = stringResource(R.string.diagnostics_classification_emulator),
         manufacturer = stringResource(R.string.diagnostics_manufacturer),
         model = stringResource(R.string.diagnostics_model),
         product = stringResource(R.string.diagnostics_product),
         battery = stringResource(R.string.diagnostics_battery),
         batteryLevel = stringResource(R.string.diagnostics_battery_level),
         charging = stringResource(R.string.diagnostics_charging),
         batterySaver = stringResource(R.string.diagnostics_battery_saver),
         thermal = stringResource(R.string.diagnostics_thermal),
         thermalStatus = stringResource(R.string.diagnostics_thermal_status),
         thermalSource = stringResource(R.string.diagnostics_thermal_source),
         thermalBattery = stringResource(R.string.diagnostics_thermal_battery),
         thermalHeadroom = stringResource(R.string.diagnostics_thermal_headroom),
         thermalSampledAt = stringResource(R.string.diagnostics_thermal_sampled_at),
    )
    OutlinedButton(onClick = { onCopy(DiagnosticsCopyFormatter.format(snapshot, labels)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_copy_diagnostics))
    }
}

@Composable
private fun DiagnosticsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}
