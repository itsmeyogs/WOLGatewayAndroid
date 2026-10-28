package com.yihs.wolgateway.tunnel.secure

interface TunnelTokenStore {
    fun read(): String?

    fun replace(token: String): String

    fun isConfigured(): Boolean
}
