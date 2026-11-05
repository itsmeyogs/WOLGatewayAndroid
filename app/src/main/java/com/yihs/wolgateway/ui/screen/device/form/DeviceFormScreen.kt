package com.yihs.wolgateway.ui.screen.device.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.validation.DeviceField
import com.yihs.wolgateway.domain.validation.ValidationMessage
import com.yihs.wolgateway.ui.screen.devices.DevicesViewModel
import kotlinx.coroutines.launch
import com.yihs.wolgateway.R

@Composable
fun DeviceFormScreen(
    deviceId: Long?,
    modifier: Modifier = Modifier,
    initialIpAddress: String = "",
    initialMacAddress: String = "",
    onSaved: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val existing by deviceId?.let { viewModel.observeDevice(it).collectAsStateWithLifecycle(initialValue = null) }
        ?: remember { mutableStateOf(null) }
    val errors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var ip by remember(existing, initialIpAddress) { mutableStateOf(existing?.ipAddress ?: initialIpAddress) }
    var mac by remember(existing, initialMacAddress) { mutableStateOf(existing?.macAddress ?: initialMacAddress) }
    var broadcast by remember(existing) { mutableStateOf(existing?.broadcastAddress ?: "255.255.255.255") }
    var wolPort by remember(existing) { mutableStateOf(existing?.wolPort?.toString() ?: "9") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(stringResource(R.string.form_field_name), name, { name = it.take(100) }, errors[DeviceField.NAME])
            FormField(stringResource(R.string.form_field_ip_address), ip, { ip = it.filter { char -> char.isDigit() || char == '.' }.take(15) }, errors[DeviceField.IP_ADDRESS], KeyboardType.Decimal)
            FormField(
                stringResource(R.string.form_field_mac_address),
                mac,
                { mac = it.filter { char -> char.isDigit() || char.lowercaseChar() in 'a'..'f' || char == ':' || char == '-' }.take(17) },
                errors[DeviceField.MAC_ADDRESS],
                placeholder = stringResource(R.string.form_mac_placeholder),
                keyboardType = KeyboardType.Ascii,
            )
            FormField(stringResource(R.string.form_field_broadcast_address), broadcast, { broadcast = it.filter { char -> char.isDigit() || char == '.' }.take(15) }, errors[DeviceField.BROADCAST_ADDRESS], KeyboardType.Decimal)
            FormField(stringResource(R.string.form_field_wol_port), wolPort, { wolPort = it.filter(Char::isDigit).take(5) }, errors[DeviceField.WOL_PORT], KeyboardType.Number)
            Text(
                stringResource(R.string.form_status_check_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val saved = viewModel.saveDevice(
                                Device(
                                    id = existing?.id ?: 0,
                                    name = name,
                                    ipAddress = ip,
                                    macAddress = mac,
                                    broadcastAddress = broadcast,
                                    wolPort = wolPort.toIntOrNull() ?: 0,
                                ),
                            )
                            saving = false
                            if (saved) onSaved()
                        }
                    },
                ) { Text(stringResource(if (saving) R.string.action_saving else R.string.action_save_device)) }
            }
        }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: ValidationMessage?,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.stringResourceId())) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun ValidationMessage.stringResourceId(): Int = when (this) {
    ValidationMessage.NAME_REQUIRED -> R.string.validation_name_required
    ValidationMessage.NAME_TOO_LONG -> R.string.validation_name_too_long
    ValidationMessage.IP_ADDRESS_INVALID -> R.string.validation_ip_address_invalid
    ValidationMessage.MAC_ADDRESS_INVALID -> R.string.validation_mac_address_invalid
    ValidationMessage.BROADCAST_ADDRESS_INVALID -> R.string.validation_broadcast_address_invalid
    ValidationMessage.WOL_PORT_INVALID -> R.string.validation_wol_port_invalid
    ValidationMessage.STATUS_CHECK_PORT_INVALID -> R.string.validation_status_check_port_invalid
}
