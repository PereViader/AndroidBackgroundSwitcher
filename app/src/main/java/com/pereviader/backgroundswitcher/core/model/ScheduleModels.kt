package com.pereviader.backgroundswitcher.core.model

import java.time.DayOfWeek
import java.time.LocalDateTime

enum class ScheduleScreenScope {
    SHARED,
    HOME,
    LOCK
}

enum class RuleType {
    TIME,
    GPS
}

data class ScheduleRule(
    val id: String,
    val listId: String,
    val listName: String = "",
    val screenScope: ScheduleScreenScope = ScheduleScreenScope.SHARED,
    val priority: Int,
    val ruleType: RuleType = RuleType.TIME,
    val startMinuteOfDay: Int = 0,
    val endMinuteOfDay: Int = 0,
    val daysOfWeekMask: Int = ALL_DAYS_MASK,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double? = null,
    val isEnabled: Boolean = true,
    val isDefaultRule: Boolean = false
) {
    companion object {
        const val ALL_DAYS_MASK = 0b1111111 // 127 (Mon..Sun)
        const val WEEKDAYS_MASK = 0b0011111 // 31 (Mon..Fri)
        const val WEEKENDS_MASK = 0b1100000 // 96 (Sat..Sun)

        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        fun defaultRuleId(scope: ScheduleScreenScope): String =
            "default_rule_${scope.name.lowercase()}"

        fun createDefaultRule(
            scope: ScheduleScreenScope,
            listId: String = WallpaperList.DEFAULT_LIST_ID,
            listName: String = WallpaperList.DEFAULT_LIST_NAME
        ): ScheduleRule = ScheduleRule(
            id = defaultRuleId(scope),
            listId = listId,
            listName = listName,
            screenScope = scope,
            priority = Int.MAX_VALUE,
            startMinuteOfDay = 0,
            endMinuteOfDay = 0,
            daysOfWeekMask = ALL_DAYS_MASK,
            isEnabled = true,
            isDefaultRule = true
        )
    }

    fun isDayActive(day: DayOfWeek): Boolean = (daysOfWeekMask and dayBit(day)) != 0
}

data class ActiveScheduleResolution(
    val screenScope: ScheduleScreenScope,
    val activeListId: String,
    val matchedRuleId: String?,
    val isFallback: Boolean
)

data class BoundaryTransition(
    val transitionTime: LocalDateTime,
    val delayMillis: Long,
    val previousListId: String,
    val nextListId: String
)
