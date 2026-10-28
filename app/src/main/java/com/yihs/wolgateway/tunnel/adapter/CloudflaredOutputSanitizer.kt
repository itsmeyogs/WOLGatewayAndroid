package com.yihs.wolgateway.tunnel.adapter

internal object CloudflaredOutputSanitizer {
    fun sanitize(line: String, secret: String): String =
        if (secret.isBlank()) line else line.replace(secret, "[REDACTED]")
}

internal fun userSafeTunnelMessage(output: String): String? =
    if (output.contains("Provided Tunnel token is not valid.")) {
        "Provided Tunnel token is not valid."
    } else {
        null
    }
