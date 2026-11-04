package com.yihs.wolgateway.diagnostics

enum class DiagnosticStatus { PASS, WARN, FAIL, NOT_CONFIGURED }

enum class DeviceClassification { AUTOMATIC_TEST, EMULATOR, REAL_DEVICE }

enum class DiagnosticsMode { STANDARD, ROOT }

data class DiagnosticItem(
    val label: String,
    val value: String,
    val status: DiagnosticStatus,
)

data class DiagnosticsSnapshot(
    val appVersion: String,
    val androidVersion: String,
    val abi: String,
    val deviceClassification: DeviceClassification,
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
    val realDevice: String,
)

object DiagnosticsCopyFormatter {
    fun format(snapshot: DiagnosticsSnapshot, labels: DiagnosticsCopyLabels): String = buildString {
        appendLine(labels.header)
        appendLine("${labels.appVersion}: ${snapshot.appVersion}")
        appendLine("${labels.android}: ${snapshot.androidVersion}")
        appendLine("${labels.abi}: ${snapshot.abi}")
        appendLine("${labels.device}: ${snapshot.deviceClassification.displayName(labels)}")
        appendLine("${labels.network}: ${snapshot.networkInterface}")
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

    private fun DeviceClassification.displayName(labels: DiagnosticsCopyLabels): String = when (this) {
        DeviceClassification.AUTOMATIC_TEST -> labels.automaticTest
        DeviceClassification.EMULATOR -> labels.emulator
        DeviceClassification.REAL_DEVICE -> labels.realDevice
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
    else -> DeviceClassification.REAL_DEVICE
}
