package com.yihs.wolgateway.data.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object GatewayCorsPreferences {
    val DEVELOPMENT_ORIGINS = stringPreferencesKey("api_cors_development_origins")
    val PRODUCTION_ORIGINS = stringPreferencesKey("api_cors_production_origins")

    const val DEFAULT_DEVELOPMENT_ORIGIN = "http://localhost:9999"

    private val listSerializer = ListSerializer(String.serializer())

    fun developmentOrigins(values: Preferences): List<String> = decode(
        values[DEVELOPMENT_ORIGINS],
        listOf(DEFAULT_DEVELOPMENT_ORIGIN),
    )

    fun productionOrigins(values: Preferences): List<String> = decode(values[PRODUCTION_ORIGINS], emptyList())

    fun allOrigins(values: Preferences): List<String> =
        (developmentOrigins(values) + productionOrigins(values)).distinct()

    fun encode(origins: List<String>): String = Json.encodeToString(listSerializer, origins)

    private fun decode(value: String?, default: List<String>): List<String> = value
        ?.let { runCatching { Json.decodeFromString(listSerializer, it) }.getOrNull() }
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?: default
}
