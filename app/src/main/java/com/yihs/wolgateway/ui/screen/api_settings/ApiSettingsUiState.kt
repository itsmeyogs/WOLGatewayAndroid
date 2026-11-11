package com.yihs.wolgateway.ui.screen.api_settings

import com.yihs.wolgateway.gateway.server.ServerState

data class ApiSettingsUiState(
    val port: String = DEFAULT_PORT.toString(),
    val serverState: ServerState = ServerState.STOPPED,
    val autoStartApi: Boolean = false,
    val tokenMode: ApiTokenMode = ApiTokenMode.AUTOMATIC,
    val tokenPreview: String = "••••••••",
    val tokenInput: String = "",
    val tokenInputError: Int? = null,
    val isEditingCustomToken: Boolean = false,
    val isLoading: Boolean = true,
    val portError: Boolean = false,
    val developmentOrigins: String = "http://localhost:9999",
    val productionOrigins: String = "",
    val corsError: Boolean = false,
    val isCorsSaving: Boolean = false,
) {
    companion object {
        const val DEFAULT_BIND_ADDRESS = "127.0.0.1"
        const val DEFAULT_PORT = 8080
        const val MIN_PORT = 1024
        const val MAX_PORT = 65_535

        fun isValidPort(port: Int?): Boolean = port in MIN_PORT..MAX_PORT
    }
}

enum class ApiTokenMode {
    AUTOMATIC,
    CUSTOM,
}
