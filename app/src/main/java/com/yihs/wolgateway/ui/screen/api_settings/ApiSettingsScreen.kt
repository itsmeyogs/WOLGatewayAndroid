package com.yihs.wolgateway.ui.screen.api_settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R
import com.yihs.wolgateway.gateway.server.ServerState

@Composable
fun ApiSettingsScreen(
    viewModel: ApiSettingsViewModel,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUrlDialog by remember { mutableStateOf(false) }
    val apiUrl = "http://${state.bindAddress}:${state.port}"

    DisposableEffect(Unit) {
        onDispose(viewModel::resetUncommittedTokenMode)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_api_description), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_api_server), style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = state.serverState == ServerState.RUNNING,
                        onCheckedChange = viewModel::setServerEnabled,
                        enabled = !state.isLoading,
                    )
                }
                Text(
                    stringResource(
                        if (state.serverState == ServerState.RUNNING) R.string.settings_api_server_running
                        else R.string.settings_api_server_stopped,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UrlCard(
                    url = apiUrl,
                    onCopy = { onCopyText(apiUrl) },
                    onEdit = { showUrlDialog = true },
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_api_token), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TokenModeOption(
                        label = stringResource(R.string.settings_token_automatic),
                        selected = state.tokenMode == ApiTokenMode.AUTOMATIC,
                        onClick = { viewModel.selectTokenMode(ApiTokenMode.AUTOMATIC) },
                        modifier = Modifier.weight(1f),
                    )
                    TokenModeOption(
                        label = stringResource(R.string.settings_token_custom),
                        selected = state.tokenMode == ApiTokenMode.CUSTOM,
                        onClick = { viewModel.selectTokenMode(ApiTokenMode.CUSTOM) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.tokenMode == ApiTokenMode.CUSTOM && state.isEditingCustomToken) {
                    OutlinedTextField(
                        value = state.tokenInput,
                        onValueChange = viewModel::onTokenInputChanged,
                        label = { Text(stringResource(R.string.settings_custom_token)) },
                        supportingText = {
                            Text(stringResource(state.tokenInputError ?: R.string.settings_custom_token_help))
                        },
                        isError = state.tokenInputError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = viewModel::saveCustomToken) {
                        Text(stringResource(R.string.action_save_token))
                    }
                } else {
                    TokenCard(
                        preview = state.tokenPreview,
                        onCopy = { viewModel.readToken()?.let(onCopyText) },
                        onEdit = if (state.tokenMode == ApiTokenMode.CUSTOM) viewModel::editCustomToken else null,
                        onRefresh = if (state.tokenMode == ApiTokenMode.AUTOMATIC) viewModel::generateToken else null,
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.dialog_edit_api_url_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = "http://${state.bindAddress}",
                        onValueChange = { value ->
                            viewModel.onBindAddressChanged(
                                value.removePrefix("http://").removePrefix("https://"),
                            )
                        },
                        label = { Text(stringResource(R.string.settings_base_url)) },
                        supportingText = { Text(stringResource(R.string.settings_base_url_help)) },
                        isError = state.bindError,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.port,
                        onValueChange = viewModel::onPortChanged,
                        label = { Text(stringResource(R.string.settings_port)) },
                        supportingText = { if (state.portError) Text(stringResource(R.string.validation_port_invalid)) },
                        isError = state.portError,
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.saveConfiguration()) showUrlDialog = false
                }) { Text(stringResource(R.string.action_save_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun UrlCard(url: String, onCopy: () -> Unit, onEdit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy_url)) }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit_api_url)) }
        }
    }
}

@Composable
private fun TokenModeOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun TokenCard(preview: String, onCopy: () -> Unit, onEdit: (() -> Unit)?, onRefresh: (() -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(preview, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy_token))
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit_token)) }
            }
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh_token)) }
            }
        }
    }
}
