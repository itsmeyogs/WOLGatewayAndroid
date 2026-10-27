package com.yihs.wolgateway.network.status

import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidStatusCheckerTest {
    @Test
    fun unknownHostIsOfflineForIcmp() = runTest {
        val result = AndroidStatusChecker().check("no-such-host.invalid", StatusCheckMethod.ICMP, 3389)

        assertEquals(DeviceStatus.OFFLINE, result.status)
        assertEquals(StatusCheckMethod.ICMP, result.method)
    }
}
