package com.yihs.wolgateway.androidservice

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayStartupPreferencesTest {
    @Test
    fun startupPreferencesKeepApiAndTunnelIndependent() = runTest {
        val directory = createTempDirectory("startup").toFile()
        val store = PreferenceDataStoreFactory.create { File(directory, "gateway.preferences_pb") }
        store.edit { it[GatewayStartupPreferences.AUTO_START_API] = true }
        val values = store.data.first()
        assertTrue(values[GatewayStartupPreferences.AUTO_START_API] == true)
        assertFalse(values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true)
    }
}
