package com.pereviader.backgroundswitcher.domain.scheduler

import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.BoundaryTransition
import com.pereviader.backgroundswitcher.core.model.RuleType
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin state machine for evaluating top-to-bottom priority wallpaper schedules
 * and calculating exact boundary transition times.
 *
 * Contains zero Android framework imports so it can be unit-tested on the JVM in milliseconds.
 */
@Singleton
class ScheduleEvaluator @Inject constructor() {

    /**
     * Evaluates the ordered priority rules from top to bottom (sorted by priority ASC)
     * for the given [screenScope] at [dateTime] and optional [currentLatitude]/[currentLongitude].
     *
     * The first enabled rule whose condition (time window or GPS radius) matches
     * (and whose list is non-empty when [nonEmptyListIds] is provided) wins.
     * If no rule matches, [fallbackListId] is returned.
     */
    fun evaluateActiveList(
        rules: List<ScheduleRule>,
        screenScope: ScheduleScreenScope,
        fallbackListId: String = WallpaperList.DEFAULT_LIST_ID,
        dateTime: LocalDateTime,
        currentLatitude: Double? = null,
        currentLongitude: Double? = null,
        nonEmptyListIds: Set<String>? = null
    ): ActiveScheduleResolution {
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute
        val currentDay = dateTime.dayOfWeek
        val previousDay = currentDay.minus(1)

        val scopedRules = rules
            .filter { it.screenScope == screenScope && it.isEnabled }
            .sortedBy { it.priority }

        for (rule in scopedRules) {
            val matches = when (rule.ruleType) {
                RuleType.TIME -> isRuleSatisfied(rule, currentDay, previousDay, minuteOfDay)
                RuleType.GPS -> isGpsRuleSatisfied(rule, currentLatitude, currentLongitude)
            }
            if (matches) {
                if (nonEmptyListIds == null || rule.listId in nonEmptyListIds) {
                    return ActiveScheduleResolution(
                        screenScope = screenScope,
                        activeListId = rule.listId,
                        matchedRuleId = rule.id,
                        isFallback = false
                    )
                }
            }
        }

        val effectiveFallback = if (
            nonEmptyListIds != null &&
            nonEmptyListIds.isNotEmpty() &&
            fallbackListId !in nonEmptyListIds &&
            WallpaperList.DEFAULT_LIST_ID in nonEmptyListIds
        ) {
            WallpaperList.DEFAULT_LIST_ID
        } else {
            fallbackListId
        }

        return ActiveScheduleResolution(
            screenScope = screenScope,
            activeListId = effectiveFallback,
            matchedRuleId = null,
            isFallback = true
        )
    }

    /**
     * Checks whether [rule] (a GPS based rule) matches the current coordinates.
     * When the user enters that area, the rule is true. When outside it is false.
     */
    fun isGpsRuleSatisfied(
        rule: ScheduleRule,
        currentLatitude: Double?,
        currentLongitude: Double?
    ): Boolean {
        if (!rule.isEnabled) return false
        val targetLat = rule.latitude ?: return false
        val targetLon = rule.longitude ?: return false
        val radius = rule.radiusMeters ?: return false
        if (currentLatitude == null || currentLongitude == null) return false

        val distance = haversineDistanceMeters(currentLatitude, currentLongitude, targetLat, targetLon)
        return distance <= radius
    }

    /**
     * Calculates great-circle distance between two geographic coordinates in meters using the Haversine formula.
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinHalfLat = Math.sin(dLat / 2)
        val sinHalfLon = Math.sin(dLon / 2)
        val a = sinHalfLat * sinHalfLat +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                sinHalfLon * sinHalfLon
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        return earthRadiusMeters * c
    }

    /**
     * Checks whether [rule] matches [minuteOfDay] on [currentDay].
     * Supports both same-day windows (e.g. 09:00 -> 17:00) and overnight windows
     * spanning midnight (e.g. 22:00 -> 06:00).
     */
    fun isRuleSatisfied(
        rule: ScheduleRule,
        currentDay: DayOfWeek,
        previousDay: DayOfWeek = currentDay.minus(1),
        minuteOfDay: Int
    ): Boolean {
        if (!rule.isEnabled || rule.daysOfWeekMask == 0) return false
        val clampedMinute = minuteOfDay.coerceIn(0, 1439)

        val start = rule.startMinuteOfDay.coerceIn(0, 1440)
        val end = rule.endMinuteOfDay.coerceIn(0, 1440)

        return when {
            start == end -> {
                // Full 24-hour window on active days
                rule.isDayActive(currentDay)
            }
            start < end -> {
                // Standard same-day window: [start, end)
                rule.isDayActive(currentDay) && clampedMinute in start until end
            }
            else -> {
                // Overnight window spanning midnight (e.g., 22:00 -> 06:00):
                // Evening portion [start, 1440) on currentDay OR early morning portion [0, end) carried from previousDay
                val isEveningMatch = rule.isDayActive(currentDay) && clampedMinute >= start
                val isMorningMatch = rule.isDayActive(previousDay) && clampedMinute < end
                isEveningMatch || isMorningMatch
            }
        }
    }

    /**
     * Finds the earliest upcoming boundary transition within the next 7 days where the active
     * list for any of the given [scopes] changes.
     */
    fun findNextBoundaryTransition(
        rules: List<ScheduleRule>,
        scopes: List<ScheduleScreenScope>,
        fallbackByScope: Map<ScheduleScreenScope, String>,
        fromDateTime: LocalDateTime,
        nonEmptyListIds: Set<String>? = null
    ): BoundaryTransition? {
        val enabledScopedRules = rules.filter { it.screenScope in scopes && it.isEnabled && it.daysOfWeekMask != 0 }
        if (enabledScopedRules.isEmpty()) return null

        val currentListsByScope = scopes.associateWith { scope ->
            evaluateActiveList(
                rules = enabledScopedRules,
                screenScope = scope,
                fallbackListId = fallbackByScope[scope] ?: WallpaperList.DEFAULT_LIST_ID,
                dateTime = fromDateTime,
                nonEmptyListIds = nonEmptyListIds
            ).activeListId
        }

        // Collect all candidate boundary minutes of day (midnight + each rule's start and end minute)
        val candidateMinutesOfDay = buildSet {
            add(0)
            for (rule in enabledScopedRules) {
                add(rule.startMinuteOfDay.coerceIn(0, 1439))
                if (rule.endMinuteOfDay in 1..1439) {
                    add(rule.endMinuteOfDay)
                }
            }
        }.sorted()

        val baseDate = fromDateTime.toLocalDate()
        for (dayOffset in 0L..7L) {
            val date = baseDate.plusDays(dayOffset)
            for (minuteOfDay in candidateMinutesOfDay) {
                val candidateTime = date.atTime(minuteOfDay / 60, minuteOfDay % 60)
                if (!candidateTime.isAfter(fromDateTime)) continue

                for (scope in scopes) {
                    val currentListId = currentListsByScope[scope] ?: WallpaperList.DEFAULT_LIST_ID
                    val nextResolution = evaluateActiveList(
                        rules = enabledScopedRules,
                        screenScope = scope,
                        fallbackListId = fallbackByScope[scope] ?: WallpaperList.DEFAULT_LIST_ID,
                        dateTime = candidateTime,
                        nonEmptyListIds = nonEmptyListIds
                    )
                    if (nextResolution.activeListId != currentListId) {
                        val delayMillis = Duration.between(fromDateTime, candidateTime).toMillis().coerceAtLeast(1000L)
                        return BoundaryTransition(
                            transitionTime = candidateTime,
                            delayMillis = delayMillis,
                            previousListId = currentListId,
                            nextListId = nextResolution.activeListId
                        )
                    }
                }
            }
        }

        return null
    }
}
