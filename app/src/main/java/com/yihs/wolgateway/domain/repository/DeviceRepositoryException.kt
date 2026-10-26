package com.yihs.wolgateway.domain.repository

enum class DuplicateDeviceField {
    IP_ADDRESS,
    MAC_ADDRESS,
}

class DuplicateDeviceException(
    val field: DuplicateDeviceField,
) : IllegalArgumentException("A device with this ${field.name.lowercase()} already exists")

class DeviceNotFoundException(id: Long) : NoSuchElementException("Device $id was not found")
