package com.yihs.wolgateway.gateway.auth

interface ApiTokenStore {
    fun getOrCreate(): String

    fun read(): String?

    fun createNew(): String

    fun replace(token: String): String

    fun isConfigured(): Boolean
}
