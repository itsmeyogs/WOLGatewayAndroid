package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.diagnostics.DiagnosticItem
import com.yihs.wolgateway.diagnostics.DiagnosticsMode
import com.yihs.wolgateway.diagnostics.DiagnosticsSnapshot
import com.yihs.wolgateway.diagnostics.DiagnosticsThermalInfo
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
data class DiagnosticsThermalSensorResponse(
    val name: String,
    val type: String,
    val temperatureCelsius: Double,
    val status: String,
    val source: String,
)

@Serializable
data class DiagnosticsThermalResponse(
    val source: String,
    val mode: String,
    val status: String,
    val batteryTemperatureCelsius: Double?,
    val thermalStatus: String?,
    val thermalHeadroom: Double?,
    val sensors: List<DiagnosticsThermalSensorResponse>,
    val sampledAt: Long,
    val available: Boolean,
    val safeError: String?,
)

@Serializable
data class DiagnosticsGeneralResponse(
    val appVersion: String,
    val androidVersion: String,
    val abi: String,
    val deviceClassification: String? = null,
    val networkInterface: String,
    val api: DiagnosticsItemResponse,
    val tunnel: DiagnosticsItemResponse,
    val gatewayService: DiagnosticsItemResponse,
    val safeErrors: List<String>,
    val device: DiagnosticsDeviceResponse,
    val battery: DiagnosticsBatteryResponse,
)

@Serializable
data class DiagnosticsServiceModeResponse(
    val effectiveMode: String,
    val fallback: Boolean,
    val data: List<DiagnosticsItemResponse>,
)

@Serializable
data class DiagnosticsResponse(
    val general: DiagnosticsGeneralResponse,
    val thermal: DiagnosticsThermalResponse,
    val serviceMode: DiagnosticsServiceModeResponse,
)

fun DiagnosticsSnapshot.toResponse() = DiagnosticsResponse(
    general = DiagnosticsGeneralResponse(
        appVersion = appVersion,
        androidVersion = androidVersion,
        abi = abi,
        deviceClassification = deviceClassification?.name,
        networkInterface = networkInterface,
        api = api.toResponse(),
        tunnel = tunnel.toResponse(),
        gatewayService = gatewayService.toResponse(),
        safeErrors = safeErrors,
        device = DiagnosticsDeviceResponse(deviceInfo.manufacturer, deviceInfo.model, deviceInfo.product),
        battery = DiagnosticsBatteryResponse(batteryInfo.levelPercent, batteryInfo.charging, batteryInfo.batterySaver),
    ),
    thermal = thermalInfo.toResponse(),
    serviceMode = DiagnosticsServiceModeResponse(
        effectiveMode = effectiveMode.name,
        fallback = selectedMode == DiagnosticsMode.ROOT && effectiveMode == DiagnosticsMode.STANDARD,
        data = (if (effectiveMode == DiagnosticsMode.ROOT) root else standard).map(DiagnosticItem::toResponse),
    ),
)

private fun DiagnosticItem.toResponse() = DiagnosticsItemResponse(
    label = label,
    value = value,
    status = status.name,
)

private fun DiagnosticsThermalInfo.toResponse() = DiagnosticsThermalResponse(
    source = source.name,
    mode = mode.name,
    status = status.name,
    batteryTemperatureCelsius = batteryTemperatureCelsius,
    thermalStatus = thermalStatus,
    thermalHeadroom = thermalHeadroom,
    sensors = sensors.map {
        DiagnosticsThermalSensorResponse(it.name, it.type, it.temperatureCelsius, it.status.name, it.source.name)
    },
    sampledAt = sampledAt,
    available = available,
    safeError = safeError,
)
