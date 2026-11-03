package com.yihs.wolgateway.root

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootModeTest {
    private val executor = object : RootCommandExecutor {
        override fun verify() = true
        override fun startGatewayService() = true
        override fun stopGatewayService() = true
        override fun isGatewayServiceRunning() = true
        override fun isApiReady(port: Int) = true
        override fun neighborMacs() = emptyMap<String, String>()
        override fun resolveNeighborMac(address: String) = null
    }
    @Test fun detectorStatesAreExplicit() {
        assertEquals(RootCapability.AVAILABLE, RootCapability.valueOf("AVAILABLE"))
        assertEquals(RootCapability.UNAVAILABLE, RootCapability.valueOf("UNAVAILABLE"))
        assertEquals(RootCapability.UNKNOWN, RootCapability.valueOf("UNKNOWN"))
    }

    @Test fun supervisorFallsBackWithoutConsent() {
        val supervisor = RootSupervisor({ RootCapability.AVAILABLE }, executor)
        assertFalse(supervisor.start(false))
        assertEquals(RootSupervisorState.FALLBACK, supervisor.state)
    }

    @Test fun supervisorRunsOnlyWithCapabilityAndConsent() {
        val supervisor = RootSupervisor({ RootCapability.AVAILABLE }, executor)
        assertTrue(supervisor.start(true))
        assertEquals(RootSupervisorState.RUNNING, supervisor.state)
        supervisor.stop()
        assertEquals(RootSupervisorState.STOPPED, supervisor.state)
    }

    @Test fun unavailableCapabilityFallsBack() {
        val supervisor = RootSupervisor({ RootCapability.UNAVAILABLE }, executor)
        assertFalse(supervisor.start(true))
        assertEquals(RootSupervisorState.FALLBACK, supervisor.state)
    }
}
