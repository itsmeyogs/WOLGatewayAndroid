package com.yihs.wolgateway.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
        val supervisor = RootSupervisor(object : RootCapabilityDetector {
            override fun detect() = RootCapability.AVAILABLE
        }, executor)
        assertFalse(supervisor.start(false))
        assertEquals(RootSupervisorState.FALLBACK, supervisor.state)
    }

    @Test fun supervisorRunsOnlyWithCapabilityAndConsent() {
        val supervisor = RootSupervisor(object : RootCapabilityDetector {
            override fun detect() = RootCapability.AVAILABLE
        }, executor)
        assertTrue(supervisor.start(true))
        assertEquals(RootSupervisorState.RUNNING, supervisor.state)
        supervisor.stop()
        assertEquals(RootSupervisorState.STOPPED, supervisor.state)
    }

    @Test fun unavailableCapabilityFallsBack() {
        val supervisor = RootSupervisor(object : RootCapabilityDetector {
            override fun detect() = RootCapability.UNAVAILABLE
        }, executor)
        assertFalse(supervisor.start(true))
        assertEquals(RootSupervisorState.FALLBACK, supervisor.state)
    }

    @Test fun failedWatchdogRestartFallsBackAfterBoundedRetries() = runBlocking {
        var starts = 0
        var running = false
        val failingExecutor = object : RootCommandExecutor {
            override fun verify() = true
            override fun startGatewayService(): Boolean {
                starts++
                running = starts == 1
                return running
            }
            override fun stopGatewayService() = true
            override fun isGatewayServiceRunning() = running
            override fun isApiReady(port: Int) = running
            override fun neighborMacs() = emptyMap<String, String>()
            override fun resolveNeighborMac(address: String) = null
        }
        val supervisor = RootSupervisor(
            object : RootCapabilityDetector { override fun detect() = RootCapability.AVAILABLE },
            failingExecutor,
            RootSupervisor.Config(Dispatchers.Default, 1, 1, 2),
        )
        assertTrue(supervisor.start(true))
        running = false
        repeat(100) {
            if (supervisor.state == RootSupervisorState.FALLBACK) return@repeat
            Thread.sleep(5)
        }
        assertEquals(RootSupervisorState.FALLBACK, supervisor.state)
        supervisor.stop()
    }
}
