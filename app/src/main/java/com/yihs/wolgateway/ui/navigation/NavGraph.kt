package com.yihs.wolgateway.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.snap
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.yihs.wolgateway.ui.screen.devices.DevicesScreen
import com.yihs.wolgateway.ui.screen.device.detail.DeviceDetailsScreen
import com.yihs.wolgateway.ui.screen.device.form.DeviceFormScreen
import com.yihs.wolgateway.ui.screen.add_devices.AddDevicesScreen
import com.yihs.wolgateway.network.discovery.DiscoveryResult
import com.yihs.wolgateway.ui.screen.settings.SettingsScreen
import com.yihs.wolgateway.ui.screen.api_settings.ApiSettingsScreen
import com.yihs.wolgateway.ui.screen.api_settings.CorsSettingsScreen
import com.yihs.wolgateway.ui.screen.devices.DevicesViewModel
import com.yihs.wolgateway.ui.screen.api_settings.ApiSettingsViewModel
import com.yihs.wolgateway.ui.screen.tunnel.TunnelScreen
import com.yihs.wolgateway.ui.screen.tunnel.TunnelViewModel
import com.yihs.wolgateway.ui.screen.settings.SettingsViewModel
import com.yihs.wolgateway.ui.screen.diagnostics.DiagnosticsScreen
import com.yihs.wolgateway.ui.screen.diagnostics.DiagnosticsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    onWake: (Long) -> Unit,
    onCheckStatus: (Long) -> Unit,
    devicesViewModel: DevicesViewModel,
    apiSettingsViewModel: ApiSettingsViewModel,
    tunnelViewModel: TunnelViewModel,
    settingsViewModel: SettingsViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun navigateOnce(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = snap()) },
        exitTransition = { fadeOut(animationSpec = snap()) },
        popEnterTransition = { fadeIn(animationSpec = snap()) },
        popExitTransition = { fadeOut(animationSpec = snap()) },
        predictivePopEnterTransition = { fadeIn(animationSpec = snap()) },
        predictivePopExitTransition = { fadeOut(animationSpec = snap()) },
        sizeTransform = { null },
    ) {
        composable(Screen.Devices.route) {
            DevicesScreen(
                onAdd = { navigateOnce(Screen.AddDevices.route) },
                onOpenDevice = { navigateOnce(Screen.Details.createRoute(it)) },
                onWake = onWake,
                viewModel = devicesViewModel,
                settingsViewModel = settingsViewModel,
            )
        }
        composable(Screen.AddDevices.route) {
            AddDevicesScreen(
                onRegisterManually = { navigateOnce(Screen.Form.createRoute()) },
                onRegisterDiscovered = { result: DiscoveryResult ->
                    navigateOnce(Screen.Form.createRoute(ipAddress = result.address, macAddress = result.macAddress))
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onOpenApi = { navigateOnce(Screen.ApiSettings.route) },
                onOpenTunnel = { navigateOnce(Screen.Tunnel.route) },
                onOpenDiagnostics = { navigateOnce(Screen.Diagnostics.route) },
                viewModel = settingsViewModel,
            )
        }
        composable(Screen.ApiSettings.route) {
            ApiSettingsScreen(
                viewModel = apiSettingsViewModel,
                onCopyText = onCopyText,
                onOpenCors = { navigateOnce(Screen.CorsSettings.route) },
            )
        }
        composable(Screen.CorsSettings.route) {
            CorsSettingsScreen(viewModel = apiSettingsViewModel)
        }
        composable(Screen.Tunnel.route) {
            TunnelScreen(
                viewModel = tunnelViewModel,
            )
        }
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(viewModel = diagnosticsViewModel, onCopy = onCopyText)
        }
        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.LongType }),
        ) { entry ->
            val deviceId = entry.arguments?.getLong("deviceId") ?: return@composable
            DeviceDetailsScreen(
                deviceId = deviceId,
                onWake = { onWake(deviceId) },
                onCheck = { onCheckStatus(deviceId) },
            )
        }
        composable(
            route = Screen.Form.route,
            arguments = listOf(
                navArgument(Screen.Form.deviceIdArgument) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(Screen.Form.ipArgument) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.Form.macArgument) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val rawDeviceId = entry.arguments?.getLong(Screen.Form.deviceIdArgument) ?: -1L
            val discoveredIp = entry.arguments?.getString(Screen.Form.ipArgument).orEmpty()
            val discoveredMac = entry.arguments?.getString(Screen.Form.macArgument).orEmpty()
            DeviceFormScreen(
                deviceId = rawDeviceId.takeUnless { it == -1L },
                initialIpAddress = discoveredIp,
                initialMacAddress = discoveredMac,
                viewModel = devicesViewModel,
                onSaved = {
                    if (rawDeviceId == -1L) {
                        navController.popBackStack(Screen.Devices.route, inclusive = false)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
