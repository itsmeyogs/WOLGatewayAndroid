package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.diagnostics.DiagnosticItem
import com.yihs.wolgateway.diagnostics.DiagnosticsBatteryInfo
import com.yihs.wolgateway.diagnostics.DiagnosticsDeviceInfo
import com.yihs.wolgateway.diagnostics.DiagnosticsSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticsItemResponse(
    val label: String,
    val value: String,
    val status: String,
)

@Serializable
data class DiagnosticsDeviceResponse(val manufacturer: String, val model: String, val product: String)

@Serializable
data class DiagnosticsBatteryResponse(val levelPercent: Int?, val charging: Boolean?, val batterySaver: Boolean?)

@Serializable
data class DiagnosticsResponse(
    val appVersion: String,
    val androidVersion: String,
    val abi: String,
    val deviceClassification: String? = null,
    val networkInterface: String,
    val selectedMode: String,
    val effectiveMode: String,
    val fallback: Boolean,
    val api: DiagnosticsItemResponse,
    val tunnel: DiagnosticsItemResponse,
    val gatewayService: DiagnosticsItemResponse,
    val safeErrors: List<String>,
    val standard: List<DiagnosticsItemResponse>,
    val root: List<DiagnosticsItemResponse>,
    val device: DiagnosticsDeviceResponse,
    val battery: DiagnosticsBatteryResponse,
)

fun DiagnosticsSnapshot.toResponse() = DiagnosticsResponse(
    appVersion = appVersion,
    androidVersion = androidVersion,
    abi = abi,
    deviceClassification = deviceClassification?.name,
    networkInterface = networkInterface,
    selectedMode = selectedMode.name,
    effectiveMode = effectiveMode.name,
    fallback = selectedMode.name == "ROOT" && effectiveMode.name == "STANDARD",
    api = api.toResponse(),
    tunnel = tunnel.toResponse(),
    gatewayService = gatewayService.toResponse(),
    safeErrors = safeErrors,
    standard = standard.map(DiagnosticItem::toResponse),
    root = root.map(DiagnosticItem::toResponse),
    device = deviceInfo.toResponse(),
    battery = batteryInfo.toResponse(),
)

private fun DiagnosticsDeviceInfo.toResponse() = DiagnosticsDeviceResponse(manufacturer, model, product)
private fun DiagnosticsBatteryInfo.toResponse() = DiagnosticsBatteryResponse(levelPercent, charging, batterySaver)

private fun DiagnosticItem.toResponse() = DiagnosticsItemResponse(
    label = label,
    value = value,
    status = status.name,
)
