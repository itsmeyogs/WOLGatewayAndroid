package com.yihs.wolgateway.tunnel.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflaredOutputSanitizerTest {
    @Test
    fun redactsTunnelTokenFromRuntimeOutput() {
        val token = "secret-tunnel-token"

        assertEquals(
            "connected with [REDACTED]",
            CloudflaredOutputSanitizer.sanitize("connected with $token", token),
        )
    }

    @Test
    fun rendersSingleCatchAllIngressRule() {
        val config = renderCloudflaredConfig("127.0.0.1:8080")

        assertEquals("ingress:\n  - service: http://127.0.0.1:8080\n", config)
        assertTrue(config.contains("ingress:"))
        assertFalse(config.contains("http_status:404"))
    }

    @Test
    fun acceptsOnlyRegisteredTunnelOutputAsReadyMarker() {
        assertTrue(isCloudflaredRegisteredOutput("Registered tunnel connection connIndex=0"))
        assertFalse(isCloudflaredRegisteredOutput("Starting tunnel tunnelID=example"))
    }

    @Test
    fun exposesOnlySafeInvalidTokenMessage() {
        val output = "Cloudflare runtime did not register a tunnel connection exitCode=255: " +
            "Provided Tunnel token is not valid. | See 'cloudflared tunnel run --help'."

        assertEquals("Provided Tunnel token is not valid.", userSafeTunnelMessage(output))
    }

    @Test
    fun usesStaticIpv4EdgesBeforeRunToBypassAndroidSrvDns() {
        val command = cloudflaredCommand("runtime", "config", "token")

        assertTrue(command.containsAll(listOf("--edge-ip-version", "4", "--edge", "198.41.192.167:7844")))
        assertEquals("run", command[command.indexOf("run")])
        assertEquals("--token-file", command[command.indexOf("run") + 1])
    }
}
