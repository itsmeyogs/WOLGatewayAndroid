package com.yihs.wolgateway.tunnel.adapter

internal object CloudflaredOutputSanitizer {
    fun sanitize(line: String, secret: String): String =
        if (secret.isBlank()) line else line.replace(secret, "[REDACTED]")
}

internal enum class TunnelSafeMessage {
    INVALID_TOKEN,
    NETWORK_UNREACHABLE,
    TRAFFIC_BLOCKED,
    CONNECTION_FAILED,
}

internal fun userSafeTunnelMessage(output: String): TunnelSafeMessage? =
    when {
        output.contains("Provided Tunnel token is not valid.") -> TunnelSafeMessage.INVALID_TOKEN
        output.contains("network is unreachable", ignoreCase = true) ||
            output.contains("API Connection failed", ignoreCase = true) ->
            TunnelSafeMessage.NETWORK_UNREACHABLE
        output.contains("port 7844", ignoreCase = true) ||
            output.contains("HTTP/2 connection is blocked", ignoreCase = true) ->
            TunnelSafeMessage.TRAFFIC_BLOCKED
        output.contains("did not register a tunnel connection", ignoreCase = true) ||
            output.contains("hard_fail=true", ignoreCase = true) ->
            TunnelSafeMessage.CONNECTION_FAILED
        else -> null
    }
