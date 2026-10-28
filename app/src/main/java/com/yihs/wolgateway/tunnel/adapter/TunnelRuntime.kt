package com.yihs.wolgateway.tunnel.adapter

import com.yihs.wolgateway.tunnel.model.TunnelRuntimeConfig

interface TunnelRuntime {
    suspend fun start(config: TunnelRuntimeConfig)

    suspend fun stop()

    fun isRunning(): Boolean
}
