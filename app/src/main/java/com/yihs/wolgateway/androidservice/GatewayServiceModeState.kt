package com.yihs.wolgateway.androidservice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class GatewayServiceMode { STANDARD, ROOT }

@Singleton
class GatewayServiceModeState @Inject constructor() {
    private val _effective = MutableStateFlow(GatewayServiceMode.STANDARD)
    val effective: StateFlow<GatewayServiceMode> = _effective.asStateFlow()

    fun set(mode: GatewayServiceMode) {
        _effective.value = mode
    }
}
