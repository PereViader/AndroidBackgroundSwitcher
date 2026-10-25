package com.pereviader.backgroundswitcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "list_photo_cross_ref",
    primaryKeys = ["listId", "photoId"],
    foreignKeys = [
        ForeignKey(
            entity = WallpaperListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["listId"]),
        Index(value = ["photoId"])
    ]
)
data class ListPhotoCrossRef(
    val listId: String,
    val photoId: String,
    val addedAt: Long = System.currentTimeMillis()
)
