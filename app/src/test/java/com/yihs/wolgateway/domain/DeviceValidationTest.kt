package com.yihs.wolgateway.domain

import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.validation.DeviceField
import com.yihs.wolgateway.domain.validation.DeviceValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceValidationTest {
    @Test
    fun validDeviceHasNoIssues() {
        assertTrue(DeviceValidator.validate(validDevice()).isEmpty())
    }

    @Test
    fun invalidDeviceReportsAllInvalidFields() {
        val issues = DeviceValidator.validate(
            validDevice().copy(name = " ", ipAddress = "not-an-ip", macAddress = "bad", wolPort = 0),
        )

        assertEquals(
            setOf(DeviceField.NAME, DeviceField.IP_ADDRESS, DeviceField.MAC_ADDRESS, DeviceField.WOL_PORT),
            issues.map { it.field }.toSet(),
        )
    }

    @Test
    fun validatorAcceptsIpv6AndNormalMacSeparators() {
        val device = validDevice().copy(
            ipAddress = "2001:db8::20",
            macAddress = "AA-BB-CC-DD-EE-FF",
        )

        assertTrue(DeviceValidator.validate(device).isEmpty())
    }

    @Test
    fun validatorRejectsMalformedMacAndNonIpv4FormInput() {
        val issues = DeviceValidator.validate(
            validDevice().copy(ipAddress = "192.168.x.20", macAddress = "AA:BB:CC:DD:EE"),
        )

        assertEquals(
            setOf(DeviceField.IP_ADDRESS, DeviceField.MAC_ADDRESS),
            issues.map { it.field }.toSet(),
        )
    }

    private fun validDevice() = Device(
        name = "Office PC",
        ipAddress = "192.168.1.20",
        macAddress = "AA:BB:CC:DD:EE:FF",
    )
}
