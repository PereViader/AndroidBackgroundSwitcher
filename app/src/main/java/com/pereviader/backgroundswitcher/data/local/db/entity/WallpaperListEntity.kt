package com.pereviader.backgroundswitcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wallpaper_lists",
    indices = [Index(value = ["orderIndex"])]
)
data class WallpaperListEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val targetScreen: String = "BOTH",
    val rotationOrder: String = "LEAST_RECENTLY_USED"
)
