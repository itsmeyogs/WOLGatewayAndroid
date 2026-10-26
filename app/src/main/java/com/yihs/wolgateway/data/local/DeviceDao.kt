package com.yihs.wolgateway.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yihs.wolgateway.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    fun observeById(id: Long): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun findById(id: Long): DeviceEntity?

    @Query("SELECT * FROM devices WHERE ipAddress = :ipAddress LIMIT 1")
    suspend fun findByIpAddress(ipAddress: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE macAddress = :macAddress LIMIT 1")
    suspend fun findByMacAddress(macAddress: String): DeviceEntity?

    @Insert
    suspend fun insert(device: DeviceEntity): Long

    @Update
    suspend fun update(device: DeviceEntity): Int

    @Delete
    suspend fun delete(device: DeviceEntity): Int
}
