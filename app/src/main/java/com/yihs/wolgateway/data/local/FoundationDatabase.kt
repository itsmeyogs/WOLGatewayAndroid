package com.yihs.wolgateway.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yihs.wolgateway.data.local.entity.FoundationEntity

@Database(entities = [FoundationEntity::class], version = 1, exportSchema = false)
abstract class FoundationDatabase : RoomDatabase()
