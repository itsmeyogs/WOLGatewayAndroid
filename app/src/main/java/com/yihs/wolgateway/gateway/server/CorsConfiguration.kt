package com.yihs.wolgateway.gateway.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureApiCors(origins: List<String>) {
    install(CORS) {
        origins.forEach { origin ->
            val parsed = CorsOrigin.parse(origin) ?: return@forEach
            allowHost(parsed.hostWithPort, schemes = listOf(parsed.scheme))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
    }
}

class CorsOrigin private constructor(
    val scheme: String,
    val hostWithPort: String,
) {
    companion object {
        fun parse(value: String): CorsOrigin? {
            if (value.isBlank() || value != value.trim() || value.contains('*')) return null
            val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            if (uri.userInfo != null || uri.path.isNotEmpty() || uri.query != null || uri.fragment != null) return null
            if (uri.port == 0 || uri.port > 65_535) return null
            val host = if (uri.host.contains(':')) "[${uri.host}]" else uri.host
            return CorsOrigin(uri.scheme, if (uri.port == -1) host else "$host:${uri.port}")
        }
    }
}

fun parseCorsOrigins(input: String): List<String>? {
    val origins = input.split(',').map(String::trim).filter(String::isNotEmpty)
    if (origins.any { CorsOrigin.parse(it) == null }) return null
    return origins.distinct()
}
