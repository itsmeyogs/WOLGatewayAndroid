package com.yihs.wolgateway.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.yihs.wolgateway.R
import com.yihs.wolgateway.ui.screen.devices.DevicesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val viewModel: DevicesViewModel = hiltViewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val wakeLabel = stringResource(R.string.action_wake)
    val checkStatusLabel = stringResource(R.string.action_check_status)
    val selectedDeviceId = backStackEntry?.arguments?.getLong("deviceId")
        ?.takeUnless { it == 0L || it == -1L }

    val title = when (currentRoute) {
        Screen.Devices.route -> stringResource(R.string.screen_devices)
        Screen.AddDevices.route -> stringResource(R.string.screen_add_devices)
        Screen.Settings.route -> stringResource(R.string.screen_settings)
        Screen.Details.route -> stringResource(R.string.screen_device_details)
        Screen.Form.route -> if (selectedDeviceId == null) {
            stringResource(R.string.screen_add_device)
        } else {
            stringResource(R.string.screen_edit_device)
        }
        else -> ""
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(
                context.resources.getString(message.resourceId, *message.arguments.toTypedArray()),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    if (currentRoute != Screen.Devices.route) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    when (currentRoute) {
                        Screen.Devices.route -> IconButton(
                            onClick = { navController.navigate(Screen.Settings.route) },
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                        Screen.Details.route -> {
                            IconButton(
                                onClick = {
                                    selectedDeviceId?.let {
                                        navController.navigate(Screen.Form.createRoute(it))
                                    }
                                },
                                enabled = selectedDeviceId != null,
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit_device))
                            }
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = selectedDeviceId != null,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_device))
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (currentRoute == Screen.Devices.route) {
                FloatingActionButton(onClick = { navController.navigate(Screen.AddDevices.route) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_device))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            startDestination = Screen.Devices.route,
            onWake = { viewModel.acknowledgeUnavailableAction(wakeLabel) },
            onCheckStatus = { viewModel.acknowledgeUnavailableAction(checkStatusLabel) },
            devicesViewModel = viewModel,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.dialog_delete_device_title)) },
            text = { Text(stringResource(R.string.dialog_delete_device_message)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedDeviceId?.let(viewModel::deleteDevice)
                    showDeleteConfirmation = false
                    navController.navigateUp()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
