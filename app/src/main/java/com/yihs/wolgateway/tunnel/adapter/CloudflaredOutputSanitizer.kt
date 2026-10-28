package com.yihs.wolgateway.tunnel.adapter

internal object CloudflaredOutputSanitizer {
    fun sanitize(line: String, secret: String): String =
        if (secret.isBlank()) line else line.replace(secret, "[REDACTED]")
}
