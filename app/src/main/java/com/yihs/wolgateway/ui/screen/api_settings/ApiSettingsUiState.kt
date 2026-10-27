package com.yihs.wolgateway.ui.screen.api_settings

import com.yihs.wolgateway.gateway.server.ServerState

data class ApiSettingsUiState(
    val bindAddress: String = DEFAULT_BIND_ADDRESS,
    val port: String = DEFAULT_PORT.toString(),
    val serverState: ServerState = ServerState.STOPPED,
    val tokenMode: ApiTokenMode = ApiTokenMode.AUTOMATIC,
    val tokenPreview: String = "••••••••",
    val tokenInput: String = "",
    val tokenInputError: Int? = null,
    val isEditingCustomToken: Boolean = false,
    val isLoading: Boolean = true,
    val portError: Boolean = false,
    val bindError: Boolean = false,
) {
    companion object {
        const val DEFAULT_BIND_ADDRESS = "127.0.0.1"
        const val DEFAULT_PORT = 8080
    }
}

enum class ApiTokenMode {
    AUTOMATIC,
    CUSTOM,
}
