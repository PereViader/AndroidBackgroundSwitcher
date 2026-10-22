package com.pereviader.backgroundswitcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val coverUri: String?,
    val photoCount: Int,
    val source: String,
    val isSelected: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
