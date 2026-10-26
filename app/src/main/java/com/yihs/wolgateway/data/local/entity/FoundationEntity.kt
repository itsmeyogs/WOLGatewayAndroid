package com.yihs.wolgateway.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foundation_metadata")
data class FoundationEntity(
    @PrimaryKey val id: Int = 1,
)
