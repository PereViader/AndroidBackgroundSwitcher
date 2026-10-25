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
    val ruleType: String = "TIME",
    val startMinuteOfDay: Int = 0,
    val endMinuteOfDay: Int = 0,
    val daysOfWeekMask: Int = 127,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double? = null,
    val isEnabled: Boolean = true,
    val isDefaultRule: Boolean = false
)
