package com.yihs.wolgateway.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import com.yihs.wolgateway.gateway.server.GatewayServer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FoundationViewModel @Inject constructor(
    private val server: GatewayServer,
) : ViewModel() {
    val title: String = "WoL Gateway"
}
