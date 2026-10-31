package com.yihs.wolgateway.tunnel.adapter

internal object CloudflaredOutputSanitizer {
    fun sanitize(line: String, secret: String): String =
        if (secret.isBlank()) line else line.replace(secret, "[REDACTED]")
}

internal fun userSafeTunnelMessage(output: String): String? =
    when {
        output.contains("Provided Tunnel token is not valid.") -> "Provided Tunnel token is not valid."
        output.contains("network is unreachable", ignoreCase = true) ||
            output.contains("API Connection failed", ignoreCase = true) ->
            "No internet connection or Cloudflare is unreachable."
        output.contains("port 7844", ignoreCase = true) ||
            output.contains("HTTP/2 connection is blocked", ignoreCase = true) ->
            "Cloudflare traffic is blocked. Allow UDP/TCP port 7844 or use an available network."
        output.contains("did not register a tunnel connection", ignoreCase = true) ||
            output.contains("hard_fail=true", ignoreCase = true) ->
            "Tunnel could not connect to Cloudflare. Check the network connection."
        else -> null
    }
