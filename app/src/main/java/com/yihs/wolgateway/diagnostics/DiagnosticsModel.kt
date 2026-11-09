package com.yihs.wolgateway.diagnostics

enum class DiagnosticStatus { PASS, WARN, FAIL, NOT_CONFIGURED }

enum class DeviceClassification { AUTOMATIC_TEST, EMULATOR, OTHER }

enum class DiagnosticsMode { STANDARD, ROOT }

enum class ThermalSource { ANDROID_PUBLIC, ROOT_ALLOWLIST, UNAVAILABLE }

enum class ThermalStatus { NOMINAL, LIGHT, MODERATE, SEVERE, CRITICAL, UNKNOWN }

data class DiagnosticItem(
    val label: String,
    val value: String,
    val status: DiagnosticStatus,
)

data class DiagnosticsDeviceInfo(
    val manufacturer: String,
    val model: String,
    val product: String,
)

data class DiagnosticsBatteryInfo(
    val levelPercent: Int?,
    val charging: Boolean?,
    val batterySaver: Boolean?,
)

data class DiagnosticsThermalSensor(
    val name: String,
    val type: String,
    val temperatureCelsius: Double,
    val status: ThermalStatus,
    val source: ThermalSource,
)

data class DiagnosticsThermalInfo(
    val source: ThermalSource,
    val mode: DiagnosticsMode,
    val status: ThermalStatus,
    val batteryTemperatureCelsius: Double?,
    val thermalStatus: String?,
    val thermalHeadroom: Double?,
    val sensors: List<DiagnosticsThermalSensor>,
    val sampledAt: Long,
    val available: Boolean,
    val safeError: String? = null,
)

data class DiagnosticsSnapshot(
    val appVersion: String,
    val androidVersion: String,
    val abi: String,
    val deviceClassification: DeviceClassification?,
    val networkInterface: String,
    val api: DiagnosticItem,
    val tunnel: DiagnosticItem,
    val gatewayService: DiagnosticItem,
    val safeErrors: List<String>,
    val mode: DiagnosticsMode,
    val standard: List<DiagnosticItem>,
    val root: List<DiagnosticItem>,
    val selectedMode: DiagnosticsMode = mode,
    val effectiveMode: DiagnosticsMode = mode,
    val deviceInfo: DiagnosticsDeviceInfo = DiagnosticsDeviceInfo("unknown", "unknown", "unknown"),
    val batteryInfo: DiagnosticsBatteryInfo = DiagnosticsBatteryInfo(null, null, null),
    val thermalInfo: DiagnosticsThermalInfo = DiagnosticsThermalInfo(
        ThermalSource.UNAVAILABLE,
        DiagnosticsMode.STANDARD,
        ThermalStatus.UNKNOWN,
        null,
        null,
        null,
        emptyList(),
        0L,
        false,
    ),
)

data class DiagnosticsCopyLabels(
    val header: String,
    val appVersion: String,
    val android: String,
    val abi: String,
    val device: String,
    val network: String,
    val api: String,
    val tunnel: String,
    val gatewayService: String,
    val rootMode: String,
    val standardMode: String,
    val errors: String,
    val none: String,
    val sensitiveRedacted: String,
    val automaticTest: String,
    val emulator: String,
    val manufacturer: String,
    val model: String,
    val product: String,
    val battery: String,
    val batteryLevel: String,
    val charging: String,
    val batterySaver: String,
    val thermal: String = "Thermal",
    val thermalStatus: String = "Thermal status",
    val thermalSource: String = "Thermal source",
    val thermalBattery: String = "Battery temperature",
    val thermalHeadroom: String = "Thermal headroom",
    val thermalSampledAt: String = "Sampled at",
)

object DiagnosticsCopyFormatter {
    fun format(snapshot: DiagnosticsSnapshot, labels: DiagnosticsCopyLabels): String = buildString {
        appendLine(labels.header)
        appendLine("${labels.appVersion}: ${snapshot.appVersion}")
        appendLine("${labels.android}: ${snapshot.androidVersion}")
        appendLine("${labels.abi}: ${snapshot.abi}")
        snapshot.deviceClassification?.let { appendLine("${labels.device}: ${it.displayName(labels)}") }
        appendLine("${labels.manufacturer}: ${snapshot.deviceInfo.manufacturer}")
        appendLine("${labels.model}: ${snapshot.deviceInfo.model}")
        appendLine("${labels.product}: ${snapshot.deviceInfo.product}")
        appendLine("${labels.network}: ${snapshot.networkInterface}")
        appendLine("${labels.battery}: ${labels.batteryLevel}=${snapshot.batteryInfo.levelPercent ?: "unknown"}, ${labels.charging}=${snapshot.batteryInfo.charging ?: "unknown"}, ${labels.batterySaver}=${snapshot.batteryInfo.batterySaver ?: "unknown"}")
        val thermal = snapshot.thermalInfo
        appendLine("${labels.thermal}: ${labels.thermalStatus}=${thermal.status}, ${labels.thermalSource}=${thermal.source}, ${labels.thermalBattery}=${thermal.batteryTemperatureCelsius?.formatCelsius() ?: "unknown"}, ${labels.thermalHeadroom}=${thermal.thermalHeadroom ?: "unknown"}, ${labels.thermalSampledAt}=${thermal.sampledAt}")
        thermal.sensors.forEach { appendLine("${it.name}: ${it.temperatureCelsius.formatCelsius()} (${it.status}, ${it.source})") }
        appendLine("${labels.api}: ${snapshot.api.value}")
        appendLine("${labels.tunnel}: ${snapshot.tunnel.value}")
        appendLine("${labels.gatewayService}: ${snapshot.gatewayService.value}")
        appendLine()
        appendLine(if (snapshot.mode == DiagnosticsMode.ROOT) labels.rootMode else labels.standardMode)
        (if (snapshot.mode == DiagnosticsMode.ROOT) snapshot.root else snapshot.standard)
            .forEach { appendLine("${it.label}: ${it.value}") }
        appendLine()
        appendLine(labels.errors)
        if (snapshot.safeErrors.isEmpty()) appendLine(labels.none)
        else snapshot.safeErrors.forEach { appendLine(safeError(it, labels.sensitiveRedacted)) }
        return toString().trimEnd()
    }

    private fun safeError(value: String, redacted: String): String {
        val normalized = value.replace(Regex("(?i)bearer\\s+\\S+"), "Bearer $redacted")
            .replace(Regex("(?i)(token|secret|credential|password)=\\S+"), "$1=$redacted")
            .replace(Regex("(?i)uid=0|su\\s+-c\\s+[^\\s]+"), redacted)
        return if (normalized.contains("/data/", ignoreCase = true) ||
            normalized.contains("/proc/", ignoreCase = true)
        ) redacted else normalized
    }

    private fun Double.formatCelsius(): String = "%.1f C".format(java.util.Locale.US, this)

    private fun DeviceClassification.displayName(labels: DiagnosticsCopyLabels): String = when (this) {
        DeviceClassification.AUTOMATIC_TEST -> labels.automaticTest
        DeviceClassification.EMULATOR -> labels.emulator
        DeviceClassification.OTHER -> labels.device
    }
}

fun classifyDevice(fingerprint: String, model: String): DeviceClassification = when {
    fingerprint.contains("robolectric", ignoreCase = true) ||
        fingerprint.contains("test-keys", ignoreCase = true) && model.contains("test", ignoreCase = true) ->
        DeviceClassification.AUTOMATIC_TEST
    fingerprint.contains("generic", ignoreCase = true) ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("emulator", ignoreCase = true) ||
        model.contains("sdk", ignoreCase = true) -> DeviceClassification.EMULATOR
    else -> DeviceClassification.OTHER
}
