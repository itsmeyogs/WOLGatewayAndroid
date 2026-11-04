package com.yihs.wolgateway.ui.screen.tunnel

import com.yihs.wolgateway.tunnel.model.TunnelState

data class TunnelUiState(
    val status: TunnelState = TunnelState.NOT_CONFIGURED,
    val tokenConfigured: Boolean = false,
    val tokenPreview: String = "••••••••",
    val tokenInput: String = "",
    val isEditingToken: Boolean = false,
    val tokenError: Int? = null,
    val autoReconnect: Boolean = false,
    val autoStartTunnel: Boolean = false,
    val error: Int? = null,
    val errorMessage: Int? = null,
)
