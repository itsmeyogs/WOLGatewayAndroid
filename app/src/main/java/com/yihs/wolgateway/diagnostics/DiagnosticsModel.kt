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
)

object DiagnosticsCopyFormatter {
    fun format(snapshot: DiagnosticsSnapshot): String = buildString {
        appendLine("WoL Gateway Diagnostics")
        appendLine("App version: ${snapshot.appVersion}")
        appendLine("Android: ${snapshot.androidVersion}")
        appendLine("ABI: ${snapshot.abi}")
        appendLine("Device: ${snapshot.deviceClassification.displayName()}")
        appendLine("Network: ${snapshot.networkInterface}")
        appendLine("API: ${snapshot.api.value}")
        appendLine("Tunnel: ${snapshot.tunnel.value}")
        appendLine("GatewayService: ${snapshot.gatewayService.value}")
        appendLine()
        appendLine(if (snapshot.mode == DiagnosticsMode.ROOT) "Root Mode" else "Standard Mode")
        (if (snapshot.mode == DiagnosticsMode.ROOT) snapshot.root else snapshot.standard)
            .forEach { appendLine("${it.label}: ${it.value}") }
        appendLine()
        appendLine("Errors")
        if (snapshot.safeErrors.isEmpty()) appendLine("None")
        else snapshot.safeErrors.forEach { appendLine(safeError(it)) }
        return toString().trimEnd()
    }

    private fun safeError(value: String): String {
        val normalized = value.replace(Regex("(?i)bearer\\s+\\S+"), "Bearer [redacted]")
            .replace(Regex("(?i)(token|secret|credential|password)=\\S+"), "$1=[redacted]")
            .replace(Regex("(?i)uid=0|su\\s+-c\\s+[^\\s]+"), "[redacted]")
        return if (normalized.contains("/data/", ignoreCase = true) ||
            normalized.contains("/proc/", ignoreCase = true)
        ) "Sensitive diagnostic detail redacted" else normalized
    }

    private fun DeviceClassification.displayName(): String = when (this) {
        DeviceClassification.AUTOMATIC_TEST -> "automatic test"
        DeviceClassification.EMULATOR -> "emulator"
        DeviceClassification.REAL_DEVICE -> "real device"
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
