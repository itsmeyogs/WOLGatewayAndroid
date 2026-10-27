package com.yihs.wolgateway.ui.navigation

sealed class Screen(val route: String) {
    data object Devices : Screen("devices")
    data object AddDevices : Screen("add-devices")
    data object Settings : Screen("settings")
    data object Details : Screen("device/{deviceId}") {
        fun createRoute(deviceId: Long): String = "device/$deviceId"
    }
    data object Form : Screen("device-form?deviceId={deviceId}") {
        const val deviceIdArgument = "deviceId"
        fun createRoute(deviceId: Long? = null): String =
            deviceId?.let { "device-form?deviceId=$it" } ?: "device-form"
    }
}
