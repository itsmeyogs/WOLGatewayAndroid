package com.yihs.wolgateway.ui.navigation

import androidx.compose.runtime.Composable
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
import com.yihs.wolgateway.ui.screen.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    onWake: (Long) -> Unit,
    onCheckStatus: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.Devices.route) {
            DevicesScreen(
                onAdd = { navController.navigate(Screen.AddDevices.route) },
                onOpenDevice = { navController.navigate(Screen.Details.createRoute(it)) },
                onWake = onWake,
                onMenu = { navController.navigate(Screen.Details.createRoute(it)) },
            )
        }
        composable(Screen.AddDevices.route) {
            AddDevicesScreen(onRegisterManually = { navController.navigate(Screen.Form.createRoute()) })
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
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
            ),
        ) { entry ->
            val rawDeviceId = entry.arguments?.getLong(Screen.Form.deviceIdArgument) ?: -1L
            DeviceFormScreen(
                deviceId = rawDeviceId.takeUnless { it == -1L },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
