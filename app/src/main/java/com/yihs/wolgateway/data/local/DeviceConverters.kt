package com.yihs.wolgateway.data.local

import androidx.room.TypeConverter
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.domain.model.StatusCheckMethod
import com.yihs.wolgateway.domain.model.WolResult

class DeviceConverters {
    @TypeConverter
    fun fromDeviceStatus(value: DeviceStatus): String = value.name

    @TypeConverter
    fun toDeviceStatus(value: String): DeviceStatus = DeviceStatus.valueOf(value)

    @TypeConverter
    fun fromStatusCheckMethod(value: StatusCheckMethod): String = value.name

    @TypeConverter
    fun toStatusCheckMethod(value: String): StatusCheckMethod = StatusCheckMethod.valueOf(value)

    @TypeConverter
    fun fromWolResult(value: WolResult): String = value.name

    @TypeConverter
    fun toWolResult(value: String): WolResult = WolResult.valueOf(value)
}
