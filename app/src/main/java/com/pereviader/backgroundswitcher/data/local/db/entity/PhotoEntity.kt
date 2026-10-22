package com.pereviader.backgroundswitcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["lastDisplayedAt"])
    ]
)
data class PhotoEntity(
    @PrimaryKey
    val id: String,
    val albumId: String,
    val uri: String,
    val width: Int = 0,
    val height: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastDisplayedAt: Long = 0L
)
