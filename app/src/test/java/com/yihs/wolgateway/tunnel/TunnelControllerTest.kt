package com.yihs.wolgateway.tunnel

import com.yihs.wolgateway.tunnel.adapter.TunnelRuntime
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.model.TunnelRuntimeConfig
import com.yihs.wolgateway.tunnel.model.TunnelState
import com.yihs.wolgateway.tunnel.secure.TunnelTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelControllerTest {
    @Test
    fun connectFailureLeavesErrorStateAndSafeUserMessage() {
        val runtime = FakeRuntime(startError = IllegalStateException(
            "Cloudflare runtime did not register a tunnel connection exitCode=255: " +
                "Provided Tunnel token is not valid. | See 'cloudflared tunnel run --help'.",
        ))
        val controller = TunnelController(runtime, FakeTokenStore())

        controller.connect("127.0.0.1:8080")
        await { controller.state.value.status == TunnelState.ERROR }

        assertEquals("Provided Tunnel token is not valid.", controller.state.value.userMessage)
    }

    @Test
    fun autoReconnectRestartsRuntimeAfterItStops() {
        val runtime = FakeRuntime()
        val controller = TunnelController(runtime, FakeTokenStore())
        controller.setAutoReconnect(true)
        controller.connect("127.0.0.1:8080")
        await { controller.state.value.status == TunnelState.CONNECTED }

        runtime.running = false
        await(timeoutMs = 3_000) { runtime.startCount >= 2 && controller.state.value.status == TunnelState.CONNECTED }

        assertTrue(runtime.startCount >= 2)
        controller.disconnect()
    }

    @Test
    fun disabledAutoReconnectDoesNotRestartRuntime() {
        val runtime = FakeRuntime()
        val controller = TunnelController(runtime, FakeTokenStore())
        controller.connect("127.0.0.1:8080")
        await { controller.state.value.status == TunnelState.CONNECTED }
        controller.setAutoReconnect(false)
        runtime.running = false
        Thread.sleep(1_200)

        assertEquals(1, runtime.startCount)
        controller.disconnect()
    }

    @Test
    fun missingTokenDoesNotStartRuntime() {
        val runtime = FakeRuntime()
        val controller = TunnelController(runtime, EmptyTokenStore())

        controller.connect("127.0.0.1:8080")
        await { controller.state.value.status == TunnelState.NOT_CONFIGURED }

        assertEquals(0, runtime.startCount)
    }

    private fun await(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition timed out", condition())
    }
}

private class FakeRuntime(
    private val startError: Exception? = null,
) : TunnelRuntime {
    @Volatile var running = false
    @Volatile var startCount = 0

    override suspend fun start(config: TunnelRuntimeConfig) {
        startCount++
        startError?.let { throw it }
        running = true
    }

    override suspend fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running
}

private class FakeTokenStore : TunnelTokenStore {
    private var token = "t".repeat(32)

    override fun read(): String = token

    override fun replace(token: String): String {
        this.token = token
        return token
    }

    override fun isConfigured(): Boolean = token.isNotBlank()
}

private class EmptyTokenStore : TunnelTokenStore {
    override fun read(): String? = null

    override fun replace(token: String): String = token

    override fun isConfigured(): Boolean = false
}
