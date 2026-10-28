package com.yihs.wolgateway.ui.screen.tunnel

import com.yihs.wolgateway.tunnel.model.TunnelState

data class TunnelUiState(
    val status: TunnelState = TunnelState.NOT_CONFIGURED,
    val tokenPreview: String = "••••••••",
    val tokenInput: String = "",
    val isEditingToken: Boolean = false,
    val tokenError: Int? = null,
    val originAddress: String = DEFAULT_ORIGIN,
    val originError: Boolean = false,
    val error: Int? = null,
) {
    companion object {
        const val DEFAULT_ORIGIN = "127.0.0.1:8080"
    }
}
