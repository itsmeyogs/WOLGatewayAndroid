package com.yihs.wolgateway.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Devices : Screen("devices")
    data object AddDevices : Screen("add-devices")
    data object Settings : Screen("settings")
    data object Details : Screen("device/{deviceId}") {
        fun createRoute(deviceId: Long): String = "device/$deviceId"
    }
    data object Form : Screen("device-form?deviceId={deviceId}&ip={ip}") {
        const val deviceIdArgument = "deviceId"
        const val ipArgument = "ip"
        fun createRoute(deviceId: Long? = null, ipAddress: String? = null): String = buildString {
            append("device-form")
            deviceId?.let { append("?deviceId=$it") }
            ipAddress?.let {
                append(if (contains("?")) '&' else '?')
                append("ip=${Uri.encode(it)}")
            }
        }
    }
}
