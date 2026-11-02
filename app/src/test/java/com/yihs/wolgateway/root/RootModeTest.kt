package com.yihs.wolgateway.root

import kotlin.test.Test
import kotlin.test.assertEquals

class RootModeTest {
    @Test fun detectorStatesAreExplicit() {
        assertEquals(RootCapability.AVAILABLE, RootCapability.valueOf("AVAILABLE"))
        assertEquals(RootCapability.UNAVAILABLE, RootCapability.valueOf("UNAVAILABLE"))
        assertEquals(RootCapability.UNKNOWN, RootCapability.valueOf("UNKNOWN"))
    }
}
