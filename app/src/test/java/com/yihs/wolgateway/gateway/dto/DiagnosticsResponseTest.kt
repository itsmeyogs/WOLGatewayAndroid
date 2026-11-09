package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.diagnostics.DiagnosticItem
import com.yihs.wolgateway.diagnostics.DiagnosticStatus
import com.yihs.wolgateway.diagnostics.DiagnosticsMode
import com.yihs.wolgateway.diagnostics.DiagnosticsSnapshot
import com.yihs.wolgateway.diagnostics.DiagnosticsThermalInfo
import com.yihs.wolgateway.diagnostics.DiagnosticsThermalSensor
import com.yihs.wolgateway.diagnostics.ThermalSource
import com.yihs.wolgateway.diagnostics.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsResponseTest {
    @Test
    fun standardModeOnlySendsStandardDiagnostics() {
        val response = snapshot(DiagnosticsMode.STANDARD).toResponse()

        assertEquals("STANDARD", response.serviceMode.effectiveMode)
        assertFalse(response.serviceMode.fallback)
        assertEquals(listOf("standard"), response.serviceMode.data.map { it.value })
        assertEquals("1.0", response.general.appVersion)
    }

    @Test
    fun rootModeOnlySendsRootDiagnostics() {
        val response = snapshot(DiagnosticsMode.ROOT).toResponse()

        assertEquals("ROOT", response.serviceMode.effectiveMode)
        assertFalse(response.serviceMode.fallback)
        assertEquals(listOf("root"), response.serviceMode.data.map { it.value })
    }

    @Test
    fun rootFallbackSendsStandardDiagnostics() {
        val response = snapshot(
            mode = DiagnosticsMode.STANDARD,
            selectedMode = DiagnosticsMode.ROOT,
            effectiveMode = DiagnosticsMode.STANDARD,
        ).toResponse()

        assertEquals("STANDARD", response.serviceMode.effectiveMode)
        assertTrue(response.serviceMode.fallback)
        assertEquals(listOf("standard"), response.serviceMode.data.map { it.value })
    }

    @Test
    fun thermalDetailsArePreservedInApiResponse() {
        val response = snapshot(DiagnosticsMode.STANDARD).copy(
            thermalInfo = DiagnosticsThermalInfo(
                ThermalSource.ANDROID_PUBLIC,
                DiagnosticsMode.STANDARD,
                ThermalStatus.LIGHT,
                34.2,
                "LIGHT",
                0.18,
                listOf(DiagnosticsThermalSensor("battery", "BATTERY", 34.2, ThermalStatus.NOMINAL, ThermalSource.ANDROID_PUBLIC)),
                1730000000000L,
                true,
            ),
        ).toResponse().thermal

        assertEquals("ANDROID_PUBLIC", response.source)
        assertEquals("LIGHT", response.status)
        assertEquals(34.2, response.batteryTemperatureCelsius!!, 0.01)
        assertEquals("battery", response.sensors.single().name)
        assertEquals(1730000000000L, response.sampledAt)
    }

    private fun snapshot(
        mode: DiagnosticsMode,
        selectedMode: DiagnosticsMode = mode,
        effectiveMode: DiagnosticsMode = mode,
    ) = DiagnosticsSnapshot(
        appVersion = "1.0",
        androidVersion = "14",
        abi = "arm64-v8a",
        deviceClassification = null,
        networkInterface = "wlan0",
        api = item("api"),
        tunnel = item("tunnel"),
        gatewayService = item("service"),
        safeErrors = emptyList(),
        mode = mode,
        standard = listOf(item("standard")),
        root = listOf(item("root")),
        selectedMode = selectedMode,
        effectiveMode = effectiveMode,
    )

    private fun item(value: String) = DiagnosticItem(value, value, DiagnosticStatus.PASS)
}
