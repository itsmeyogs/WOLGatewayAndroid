package com.yihs.wolgateway.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.WolResult

@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["macAddress"], unique = true),
    ],
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String,
    val wolPort: Int,
    val statusCheckMethod: StatusCheckMethod,
    val statusCheckPort: Int,
    val status: DeviceStatus,
    val lastCheckAt: Long?,
    val lastCheckReason: String?,
    val lastWolResult: WolResult,
    val lastWolAt: Long?,
)
