package com.pereviader.backgroundswitcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_rules",
    foreignKeys = [
        ForeignKey(
            entity = WallpaperListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["screenScope", "priority"]),
        Index(value = ["listId"])
    ]
)
data class ScheduleRuleEntity(
    @PrimaryKey
    val id: String,
    val listId: String,
    val screenScope: String,
    val priority: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysOfWeekMask: Int,
    val isEnabled: Boolean = true,
    val isDefaultRule: Boolean = false
)
