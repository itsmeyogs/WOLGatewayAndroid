package com.yihs.wolgateway.tunnel

import com.yihs.wolgateway.ui.screen.tunnel.localApiOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelOriginTest {
    @Test
    fun usesApiLoopbackAndConfiguredPort() {
        assertEquals("127.0.0.1:9090", localApiOrigin(9090))
    }
}
