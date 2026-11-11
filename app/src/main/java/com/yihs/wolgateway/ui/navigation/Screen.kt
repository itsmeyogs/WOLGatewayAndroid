package com.yihs.wolgateway.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Devices : Screen("devices")
    data object AddDevices : Screen("add-devices")
    data object Settings : Screen("settings")
    data object ApiSettings : Screen("settings/api")
    data object CorsSettings : Screen("settings/api/cors")
    data object Tunnel : Screen("settings/tunnel")
    data object Diagnostics : Screen("settings/diagnostics")
    data object Details : Screen("device/{deviceId}") {
        fun createRoute(deviceId: Long): String = "device/$deviceId"
    }
    data object Form : Screen("device-form?deviceId={deviceId}&ip={ip}&mac={mac}") {
        const val deviceIdArgument = "deviceId"
        const val ipArgument = "ip"
        const val macArgument = "mac"
        fun createRoute(deviceId: Long? = null, ipAddress: String? = null, macAddress: String? = null): String = buildString {
            append("device-form")
            deviceId?.let { append("?deviceId=$it") }
            ipAddress?.let {
                append(if (contains("?")) '&' else '?')
                append("ip=${Uri.encode(it)}")
            }
            macAddress?.takeIf { it.isNotBlank() }?.let {
                append(if (contains("?")) '&' else '?')
                append("mac=${Uri.encode(it)}")
            }
        }
    }
}
