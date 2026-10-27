package com.yihs.wolgateway.domain.model

data class Device(
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val wolPort: Int = 9,
    val statusCheckMethod: StatusCheckMethod = StatusCheckMethod.ICMP_AND_TCP,
    val statusCheckPort: Int = 3389,
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    val lastCheckAt: Long? = null,
    val lastCheckLatencyMs: Long? = null,
    val lastCheckReason: String? = null,
    val lastWolResult: WolResult = WolResult.NOT_ATTEMPTED,
    val lastWolAt: Long? = null,
)
