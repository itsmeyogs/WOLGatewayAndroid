package com.yihs.wolgateway.ui.screen.api_settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSettingsUiStateTest {
    @Test
    fun apiPortAcceptsOnlyAndroidUnprivilegedRange() {
        assertFalse(ApiSettingsUiState.isValidPort(null))
        assertFalse(ApiSettingsUiState.isValidPort(80))
        assertTrue(ApiSettingsUiState.isValidPort(ApiSettingsUiState.MIN_PORT))
        assertTrue(ApiSettingsUiState.isValidPort(8081))
        assertTrue(ApiSettingsUiState.isValidPort(ApiSettingsUiState.MAX_PORT))
        assertFalse(ApiSettingsUiState.isValidPort(ApiSettingsUiState.MAX_PORT + 1))
    }
}
