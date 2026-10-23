package com.pereviader.backgroundswitcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores wallpaper customization parameters (pan offsets and zoom scale) per image URI.
 * This guarantees customizations are decoupled from transient album mappings, persisted
 * per image, and cleanly deleted when the image can no longer be referenced.
 */
@Entity(tableName = "wallpaper_customizations")
data class WallpaperCustomizationEntity(
    @PrimaryKey
    val imageUri: String,
    val cropX: Float? = null,
    val cropY: Float? = null,
    val zoom: Float? = null,
    val isEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
