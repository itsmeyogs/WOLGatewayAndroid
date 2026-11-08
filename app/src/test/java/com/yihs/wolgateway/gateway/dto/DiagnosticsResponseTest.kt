package com.yihs.wolgateway.gateway.dto

import com.yihs.wolgateway.diagnostics.DiagnosticItem
import com.yihs.wolgateway.diagnostics.DiagnosticStatus
import com.yihs.wolgateway.diagnostics.DiagnosticsMode
import com.yihs.wolgateway.diagnostics.DiagnosticsSnapshot
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
