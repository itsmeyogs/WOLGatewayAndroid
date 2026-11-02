package com.yihs.wolgateway.root

data class RootModeState(
    val enabled: Boolean = false,
    val consented: Boolean = false,
    val capability: RootCapability = RootCapability.UNKNOWN,
    val fallback: Boolean = false,
)
