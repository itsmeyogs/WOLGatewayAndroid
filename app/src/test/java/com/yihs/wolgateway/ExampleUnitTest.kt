package com.yihs.wolgateway

import org.junit.Assert.*
import org.junit.Test
import com.yihs.wolgateway.gateway.server.ServerState

class FoundationUnitTest {
    @Test
    fun serverStateStartsStopped() {
        assertEquals(ServerState.STOPPED, ServerState.values().first())
    }
}
