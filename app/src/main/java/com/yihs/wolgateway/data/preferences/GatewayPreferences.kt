package com.yihs.wolgateway.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.gatewayPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gateway_preferences",
)

fun Context.gatewayPreferences(): DataStore<Preferences> = gatewayPreferencesDataStore
