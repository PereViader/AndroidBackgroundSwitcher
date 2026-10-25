package com.pereviader.backgroundswitcher.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleEvaluatorTest {

    private val evaluator = ScheduleEvaluator()

    private val workRule = ScheduleRule(
        id = "rule_work",
        listId = "list_work",
        listName = "Work Hours",
        screenScope = ScheduleScreenScope.SHARED,
        priority = 0,
        startMinuteOfDay = 9 * 60, // 09:00
        endMinuteOfDay = 17 * 60,  // 17:00
        daysOfWeekMask = ScheduleRule.WEEKDAYS_MASK, // Mon-Fri
        isEnabled = true
    )

    private val eveningRule = ScheduleRule(
        id = "rule_evening",
        listId = "list_evening",
        listName = "Evening",
        screenScope = ScheduleScreenScope.SHARED,
        priority = 1,
        startMinuteOfDay = 18 * 60, // 18:00
        endMinuteOfDay = 23 * 60,   // 23:00
        daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK,
        isEnabled = true
    )

    private val overnightRule = ScheduleRule(
        id = "rule_night",
        listId = "list_night",
        listName = "Night Mode",
        screenScope = ScheduleScreenScope.SHARED,
        priority = 2,
        startMinuteOfDay = 22 * 60, // 22:00
        endMinuteOfDay = 6 * 60,    // 06:00 next day
        daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK,
        isEnabled = true
    )

    @Test
    fun `matches daytime rule within start and end boundaries on active weekday`() {
        val wednesday1030 = LocalDateTime.of(2026, 3, 25, 10, 30)

        val resolution = evaluator.evaluateActiveList(
            rules = listOf(workRule, eveningRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = wednesday1030
        )

        assertThat(resolution.activeListId).isEqualTo("list_work")
        assertThat(resolution.matchedRuleId).isEqualTo("rule_work")
        assertThat(resolution.isFallback).isFalse()
    }

    @Test
    fun `start boundary is inclusive and end boundary is exclusive`() {
        val exactStart = LocalDateTime.of(2026, 3, 25, 9, 0)
        val exactEnd = LocalDateTime.of(2026, 3, 25, 17, 0)

        val atStart = evaluator.evaluateActiveList(
            rules = listOf(workRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = exactStart
        )
        assertThat(atStart.activeListId).isEqualTo("list_work")
        assertThat(atStart.matchedRuleId).isEqualTo("rule_work")

        val atEnd = evaluator.evaluateActiveList(
            rules = listOf(workRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = exactEnd
        )
        assertThat(atEnd.activeListId).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
        assertThat(atEnd.matchedRuleId).isNull()
        assertThat(atEnd.isFallback).isTrue()
    }

    @Test
    fun `does not match weekday rule on weekend and falls back to default list`() {
        val saturday1100 = LocalDateTime.of(2026, 3, 28, 11, 0)

        val resolution = evaluator.evaluateActiveList(
            rules = listOf(workRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = saturday1100
        )

        assertThat(resolution.activeListId).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
        assertThat(resolution.matchedRuleId).isNull()
        assertThat(resolution.isFallback).isTrue()
    }

    @Test
    fun `top-to-bottom priority order wins when multiple rules overlap`() {
        val wednesday2230 = LocalDateTime.of(2026, 3, 25, 22, 30)

        val resolution = evaluator.evaluateActiveList(
            rules = listOf(workRule, eveningRule, overnightRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = wednesday2230
        )

        assertThat(resolution.activeListId).isEqualTo("list_evening")
        assertThat(resolution.matchedRuleId).isEqualTo("rule_evening")

        val swappedRules = listOf(
            overnightRule.copy(priority = 0),
            eveningRule.copy(priority = 1)
        )
        val swappedResolution = evaluator.evaluateActiveList(
            rules = swappedRules,
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = wednesday2230
        )
        assertThat(swappedResolution.activeListId).isEqualTo("list_night")
        assertThat(swappedResolution.matchedRuleId).isEqualTo("rule_night")
    }

    @Test
    fun `overnight window spanning midnight matches both before midnight and after midnight`() {
        val fridayMask = ScheduleRule.dayBit(DayOfWeek.FRIDAY)
        val fridayNightRule = overnightRule.copy(daysOfWeekMask = fridayMask)

        val friday2315 = LocalDateTime.of(2026, 3, 27, 23, 15)
        val resFridayLate = evaluator.evaluateActiveList(
            rules = listOf(fridayNightRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = friday2315
        )
        assertThat(resFridayLate.activeListId).isEqualTo("list_night")

        val saturday0230 = LocalDateTime.of(2026, 3, 28, 2, 30)
        val resSaturdayEarly = evaluator.evaluateActiveList(
            rules = listOf(fridayNightRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = saturday0230
        )
        assertThat(resSaturdayEarly.activeListId).isEqualTo("list_night")

        val saturday2315 = LocalDateTime.of(2026, 3, 28, 23, 15)
        val resSaturdayLate = evaluator.evaluateActiveList(
            rules = listOf(fridayNightRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = saturday2315
        )
        assertThat(resSaturdayLate.activeListId).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
    }

    @Test
    fun `disabled rules are skipped`() {
        val wednesday1030 = LocalDateTime.of(2026, 3, 25, 10, 30)
        val disabledWorkRule = workRule.copy(isEnabled = false)

        val resolution = evaluator.evaluateActiveList(
            rules = listOf(disabledWorkRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = wednesday1030
        )

        assertThat(resolution.activeListId).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
        assertThat(resolution.matchedRuleId).isNull()
    }

    @Test
    fun `skips rule when target list is empty and falls back to lower priority rule or fallback list`() {
        val wednesday2230 = LocalDateTime.of(2026, 3, 25, 22, 30)

        val resolution = evaluator.evaluateActiveList(
            rules = listOf(eveningRule, overnightRule),
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = WallpaperList.DEFAULT_LIST_ID,
            dateTime = wednesday2230,
            nonEmptyListIds = setOf("list_night", WallpaperList.DEFAULT_LIST_ID)
        )

        assertThat(resolution.activeListId).isEqualTo("list_night")
        assertThat(resolution.matchedRuleId).isEqualTo("rule_night")
    }

    @Test
    fun `evaluates HOME and LOCK scopes independently`() {
        val wednesday1030 = LocalDateTime.of(2026, 3, 25, 10, 30)
        val homeRule = workRule.copy(
            id = "home_rule",
            screenScope = ScheduleScreenScope.HOME,
            listId = "list_home_focus"
        )
        val lockRule = workRule.copy(
            id = "lock_rule",
            screenScope = ScheduleScreenScope.LOCK,
            listId = "list_lock_art"
        )

        val homeResolution = evaluator.evaluateActiveList(
            rules = listOf(homeRule, lockRule),
            screenScope = ScheduleScreenScope.HOME,
            fallbackListId = "fallback_home",
            dateTime = wednesday1030
        )
        val lockResolution = evaluator.evaluateActiveList(
            rules = listOf(homeRule, lockRule),
            screenScope = ScheduleScreenScope.LOCK,
            fallbackListId = "fallback_lock",
            dateTime = wednesday1030
        )

        assertThat(homeResolution.activeListId).isEqualTo("list_home_focus")
        assertThat(lockResolution.activeListId).isEqualTo("list_lock_art")
    }

    @Test
    fun `findNextBoundaryTransition computes exact delay to next start or end boundary`() {
        val wednesday0830 = LocalDateTime.of(2026, 3, 25, 8, 30, 0)
        val fallbacks = mapOf(ScheduleScreenScope.SHARED to WallpaperList.DEFAULT_LIST_ID)

        val transitionToStart = evaluator.findNextBoundaryTransition(
            rules = listOf(workRule),
            scopes = listOf(ScheduleScreenScope.SHARED),
            fallbackByScope = fallbacks,
            fromDateTime = wednesday0830
        )

        assertThat(transitionToStart).isNotNull()
        assertThat(transitionToStart!!.transitionTime).isEqualTo(LocalDateTime.of(2026, 3, 25, 9, 0, 0))
        assertThat(transitionToStart.delayMillis).isEqualTo(30L * 60L * 1000L)
        assertThat(transitionToStart.previousListId).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
        assertThat(transitionToStart.nextListId).isEqualTo("list_work")

        val wednesday1000 = LocalDateTime.of(2026, 3, 25, 10, 0, 0)
        val transitionToEnd = evaluator.findNextBoundaryTransition(
            rules = listOf(workRule),
            scopes = listOf(ScheduleScreenScope.SHARED),
            fallbackByScope = fallbacks,
            fromDateTime = wednesday1000
        )

        assertThat(transitionToEnd).isNotNull()
        assertThat(transitionToEnd!!.transitionTime).isEqualTo(LocalDateTime.of(2026, 3, 25, 17, 0, 0))
        assertThat(transitionToEnd.delayMillis).isEqualTo(7L * 60L * 60L * 1000L)
        assertThat(transitionToEnd.previousListId).isEqualTo("list_work")
        assertThat(transitionToEnd.nextListId).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
    }
}
