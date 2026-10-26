package com.yihs.wolgateway.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yihs.wolgateway.data.local.entity.DeviceEntity

@Database(entities = [DeviceEntity::class], version = 2, exportSchema = false)
@TypeConverters(DeviceConverters::class)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}
