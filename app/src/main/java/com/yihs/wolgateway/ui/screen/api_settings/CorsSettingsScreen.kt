package com.yihs.wolgateway.ui.screen.api_settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yihs.wolgateway.R

@Composable
fun CorsSettingsScreen(
    viewModel: ApiSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.settings_api_cors), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_api_cors_note), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = state.developmentOrigins,
                    onValueChange = viewModel::onDevelopmentOriginsChanged,
                    label = { Text(stringResource(R.string.settings_api_cors_development)) },
                    supportingText = { Text(stringResource(R.string.settings_api_cors_origin_help)) },
                    isError = state.corsError,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.productionOrigins,
                    onValueChange = viewModel::onProductionOriginsChanged,
                    label = { Text(stringResource(R.string.settings_api_cors_production)) },
                    supportingText = { Text(stringResource(R.string.settings_api_cors_origin_help)) },
                    isError = state.corsError,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::saveCorsConfiguration,
                    enabled = !state.isCorsSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (state.isCorsSaving) R.string.action_saving else R.string.action_save_restart_service))
                }
            }
        }
    }
}
