package com.yihs.wolgateway.domain.validation

import com.yihs.wolgateway.domain.model.Device
import java.net.InetAddress

enum class DeviceField {
    NAME,
    IP_ADDRESS,
    MAC_ADDRESS,
    BROADCAST_ADDRESS,
    WOL_PORT,
    STATUS_CHECK_PORT,
}

data class ValidationIssue(
    val field: DeviceField,
    val message: ValidationMessage,
)

class InvalidDeviceException(
    val issues: List<ValidationIssue>,
) : IllegalArgumentException("Device validation failed: ${issues.joinToString { it.field.name }}")

object DeviceValidator {
    private val macPattern = Regex("^[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}$")

    fun validate(device: Device): List<ValidationIssue> = buildList {
        if (device.name.trim().isEmpty()) {
            add(ValidationIssue(DeviceField.NAME, ValidationMessage.NAME_REQUIRED))
        } else if (device.name.trim().length > 100) {
            add(ValidationIssue(DeviceField.NAME, ValidationMessage.NAME_TOO_LONG))
        }
        if (!isIpAddress(device.ipAddress)) {
            add(ValidationIssue(DeviceField.IP_ADDRESS, ValidationMessage.IP_ADDRESS_INVALID))
        }
        if (!macPattern.matches(device.macAddress.trim())) {
            add(ValidationIssue(DeviceField.MAC_ADDRESS, ValidationMessage.MAC_ADDRESS_INVALID))
        }
        if (!isIpAddress(device.broadcastAddress)) {
            add(ValidationIssue(DeviceField.BROADCAST_ADDRESS, ValidationMessage.BROADCAST_ADDRESS_INVALID))
        }
        if (device.wolPort !in 1..65535) {
            add(ValidationIssue(DeviceField.WOL_PORT, ValidationMessage.WOL_PORT_INVALID))
        }
        if (device.statusCheckPort !in 1..65535) {
            add(ValidationIssue(DeviceField.STATUS_CHECK_PORT, ValidationMessage.STATUS_CHECK_PORT_INVALID))
        }
    }

    fun requireValid(device: Device) {
        val issues = validate(device)
        if (issues.isNotEmpty()) throw InvalidDeviceException(issues)
    }

    private fun isIpAddress(value: String): Boolean = runCatching {
        val candidate = value.trim()
        when {
            candidate.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) ->
                candidate.split('.').all { it.toInt() in 0..255 }
            candidate.contains(':') -> InetAddress.getByName(candidate).address.size == 16
            else -> false
        }
    }.getOrDefault(false)
}
