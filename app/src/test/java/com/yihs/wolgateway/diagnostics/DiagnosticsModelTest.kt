package com.yihs.wolgateway.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsModelTest {
    @Test
    fun classifiesTestEmulatorAndRealDevice() {
        assertTrue(classifyDevice("robolectric", "test") == DeviceClassification.AUTOMATIC_TEST)
        assertTrue(classifyDevice("generic_x86_64", "sdk_gphone") == DeviceClassification.EMULATOR)
        assertTrue(classifyDevice("google/panther/panther", "Pixel 7") == DeviceClassification.REAL_DEVICE)
    }

    @Test
    fun copyOutputUsesOnlySafeFields() {
        val secret = "api-secret-123"
        val snapshot = DiagnosticsSnapshot(
            appVersion = "1.0",
            androidVersion = "14",
            abi = "arm64-v8a",
            deviceClassification = DeviceClassification.REAL_DEVICE,
            networkInterface = "wlan0",
            api = DiagnosticItem("API", "running", DiagnosticStatus.PASS),
            tunnel = DiagnosticItem("Tunnel", "configured", DiagnosticStatus.PASS),
            gatewayService = DiagnosticItem("GatewayService", "running", DiagnosticStatus.PASS),
            safeErrors = listOf("safe error", "Bearer $secret", "uid=0", "/data/user/0/secret"),
            mode = DiagnosticsMode.STANDARD,
            standard = listOf(DiagnosticItem("Token", "configured", DiagnosticStatus.PASS)),
            root = emptyList(),
        )

        val output = DiagnosticsCopyFormatter.format(snapshot)
        assertTrue(output.contains("Token: configured"))
        assertFalse(output.contains(secret))
        assertFalse(output.contains("uid=0"))
        assertFalse(output.contains("su -c"))
        assertFalse(output.contains(secret))
        assertFalse(output.contains("/data/user/0/secret"))
    }

    @Test
    fun copyOutputIsStable() {
        val item = DiagnosticItem("API", "running", DiagnosticStatus.PASS)
        val snapshot = DiagnosticsSnapshot(
            "1.0", "14", "arm64-v8a", DeviceClassification.EMULATOR, "wlan0",
            item, item, item, emptyList(), DiagnosticsMode.STANDARD, emptyList(), emptyList(),
        )
        assertTrue(DiagnosticsCopyFormatter.format(snapshot) == DiagnosticsCopyFormatter.format(snapshot))
    }
}
