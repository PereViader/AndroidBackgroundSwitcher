package com.pereviader.backgroundswitcher.ui.lists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListsAndScheduleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultList = WallpaperList(
        id = WallpaperList.DEFAULT_LIST_ID,
        name = WallpaperList.DEFAULT_LIST_NAME,
        isDefault = true,
        photoCount = 2
    )
    private val workList = WallpaperList(
        id = "list_work",
        name = "Work Focus",
        isDefault = false,
        photoCount = 5
    )

    private val defaultRes = ActiveScheduleResolution(
        screenScope = ScheduleScreenScope.SHARED,
        activeListId = WallpaperList.DEFAULT_LIST_ID,
        matchedRuleId = null,
        isFallback = true
    )

    @Test
    fun `rules screen shows schedule directly and segmented row and photo lists content do not exist`() {
        composeTestRule.setContent {
            RulesScreen(
                lists = listOf(defaultList, workList),
                isSeparateScreensMode = false,
                rulesShared = emptyList(),
                rulesHome = emptyList(),
                rulesLock = emptyList(),
                resolutionShared = defaultRes,
                resolutionHome = defaultRes.copy(screenScope = ScheduleScreenScope.HOME),
                resolutionLock = defaultRes.copy(screenScope = ScheduleScreenScope.LOCK),
                fallbackListIdShared = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdHome = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdLock = WallpaperList.DEFAULT_LIST_ID,
                onToggleSeparateScreensMode = {},
                onSaveRule = {},
                onDeleteRule = {},
                onToggleRuleEnabled = { _, _ -> },
                onSetFallbackList = { _, _ -> },
                onCopyScheduleScope = { _, _ -> }
            )
        }

        // Segmented control is removed
        composeTestRule.onNodeWithTag("lists_schedule_segmented_row").assertDoesNotExist()
        composeTestRule.onNodeWithTag("segment_photo_lists").assertDoesNotExist()
        composeTestRule.onNodeWithTag("segment_priority_schedule").assertDoesNotExist()
        composeTestRule.onNodeWithText("Your Photo Lists").assertDoesNotExist()

        // Rules functionality (Priority Schedule) is directly visible
        composeTestRule.onNodeWithText("Priority Schedule").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_schedule_rule_button").assertIsDisplayed()
    }

    @Test
    fun `rules screen shows rules, active indicator within rule, single add button, and supports priority reordering`() {
        var movedRuleId: String? = null
        var movedUpFlag: Boolean? = null

        val rule1 = ScheduleRule(
            id = "r1",
            listId = "list_work",
            listName = "Work Focus",
            screenScope = ScheduleScreenScope.SHARED,
            priority = 0,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            daysOfWeekMask = ScheduleRule.WEEKDAYS_MASK
        )
        val rule2 = ScheduleRule(
            id = "r2",
            listId = WallpaperList.DEFAULT_LIST_ID,
            listName = WallpaperList.DEFAULT_LIST_NAME,
            screenScope = ScheduleScreenScope.SHARED,
            priority = 1,
            startMinuteOfDay = 10 * 60,
            endMinuteOfDay = 20 * 60,
            daysOfWeekMask = ScheduleRule.WEEKENDS_MASK
        )

        composeTestRule.setContent {
            RulesScreen(
                lists = listOf(defaultList, workList),
                isSeparateScreensMode = false,
                rulesShared = listOf(rule1, rule2),
                rulesHome = emptyList(),
                rulesLock = emptyList(),
                resolutionShared = ActiveScheduleResolution(
                    screenScope = ScheduleScreenScope.SHARED,
                    activeListId = "list_work",
                    matchedRuleId = "r1",
                    isFallback = false
                ),
                resolutionHome = defaultRes.copy(screenScope = ScheduleScreenScope.HOME),
                resolutionLock = defaultRes.copy(screenScope = ScheduleScreenScope.LOCK),
                fallbackListIdShared = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdHome = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdLock = WallpaperList.DEFAULT_LIST_ID,
                onToggleSeparateScreensMode = {},
                onSaveRule = {},
                onDeleteRule = {},
                onToggleRuleEnabled = { _, _ -> },
                onMoveRulePriority = { _, ruleId, moveUp ->
                    movedRuleId = ruleId
                    movedUpFlag = moveUp
                },
                onSetFallbackList = { _, _ -> },
                onCopyScheduleScope = { _, _ -> }
            )
        }

        // Single Add Rule button is displayed in header
        composeTestRule.onNodeWithTag("add_schedule_rule_button").assertIsDisplayed()

        // Redundant top active banner does not exist
        composeTestRule.onNodeWithTag("active_schedule_banner").assertDoesNotExist()

        // Active indicator is unified and displayed directly within the matching rule element
        composeTestRule.onNodeWithTag("schedule_lazy_column")
            .performScrollToNode(hasTestTag("schedule_rule_card_r1"))
        composeTestRule.onNodeWithTag("schedule_rule_card_r1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rule_active_now_badge_r1").assertIsDisplayed()

        // Inactive rule does not display active badge
        composeTestRule.onNodeWithTag("rule_active_now_badge_r2").assertDoesNotExist()

        // Buttons for move up, move down, and edit are removed (Subagent 3)
        composeTestRule.onNodeWithTag("rule_move_down_r1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rule_move_up_r1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rule_edit_r1").assertDoesNotExist()

        // Drag handle icons are displayed on the left (Subagent 3)
        composeTestRule.onNodeWithTag("rule_drag_handle_r1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rule_drag_handle_r2").assertIsDisplayed()

        // Reorder rules by dragging drag handle down on r1
        composeTestRule.onNodeWithTag("rule_drag_handle_r1").performTouchInput {
            swipeDown()
        }
        assertThat(movedRuleId).isEqualTo("r1")
        assertThat(movedUpFlag).isFalse()

        // Clicking the card opens rule editor (Subagent 3)
        composeTestRule.onNodeWithTag("schedule_rule_card_r1").performClick()
        composeTestRule.onNodeWithTag("schedule_rule_editor_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithTag("schedule_rule_editor_dialog").assertDoesNotExist()
    }

    @Test
    fun `when Separate Screens mode is enabled, Home Screen and Lock Screen tabs switch scopes independently`() {
        val homeRule = ScheduleRule(
            id = "r_home",
            listId = "list_work",
            listName = "Work Focus",
            screenScope = ScheduleScreenScope.HOME,
            priority = 0,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 18 * 60,
            daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK
        )
        val lockRule = ScheduleRule(
            id = "r_lock",
            listId = WallpaperList.DEFAULT_LIST_ID,
            listName = WallpaperList.DEFAULT_LIST_NAME,
            screenScope = ScheduleScreenScope.LOCK,
            priority = 0,
            startMinuteOfDay = 20 * 60,
            endMinuteOfDay = 6 * 60,
            daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK
        )

        composeTestRule.setContent {
            RulesScreen(
                lists = listOf(defaultList, workList),
                isSeparateScreensMode = true,
                rulesShared = emptyList(),
                rulesHome = listOf(homeRule),
                rulesLock = listOf(lockRule),
                resolutionShared = defaultRes,
                resolutionHome = ActiveScheduleResolution(ScheduleScreenScope.HOME, "list_work", "r_home", false),
                resolutionLock = ActiveScheduleResolution(ScheduleScreenScope.LOCK, WallpaperList.DEFAULT_LIST_ID, "r_lock", false),
                fallbackListIdShared = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdHome = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdLock = WallpaperList.DEFAULT_LIST_ID,
                onToggleSeparateScreensMode = {},
                onSaveRule = {},
                onDeleteRule = {},
                onToggleRuleEnabled = { _, _ -> },
                onMoveRulePriority = { _, _, _ -> },
                onSetFallbackList = { _, _ -> },
                onCopyScheduleScope = { _, _ -> }
            )
        }

        // Home Screen tab is active initially
        composeTestRule.onNodeWithTag("separate_screen_tabs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_tab_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_tab_lock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_lazy_column")
            .performScrollToNode(hasTestTag("schedule_rule_card_r_home"))
        composeTestRule.onNodeWithTag("schedule_rule_card_r_home").assertIsDisplayed()

        // Switch to Lock Screen tab
        composeTestRule.onNodeWithTag("schedule_tab_lock").performClick()
        composeTestRule.onNodeWithTag("schedule_lazy_column")
            .performScrollToNode(hasTestTag("schedule_rule_card_r_lock"))
        composeTestRule.onNodeWithTag("schedule_rule_card_r_lock").assertIsDisplayed()
    }

    @Test
    fun `priority schedule always displays unremovable default rule marked active when rules list is empty`() {
        var selectedFallback: String? = null

        composeTestRule.setContent {
            RulesScreen(
                lists = listOf(defaultList, workList),
                isSeparateScreensMode = false,
                rulesShared = emptyList(),
                rulesHome = emptyList(),
                rulesLock = emptyList(),
                resolutionShared = defaultRes,
                resolutionHome = defaultRes.copy(screenScope = ScheduleScreenScope.HOME),
                resolutionLock = defaultRes.copy(screenScope = ScheduleScreenScope.LOCK),
                fallbackListIdShared = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdHome = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdLock = WallpaperList.DEFAULT_LIST_ID,
                onToggleSeparateScreensMode = {},
                onSaveRule = {},
                onDeleteRule = {},
                onToggleRuleEnabled = { _, _ -> },
                onMoveRulePriority = { _, _, _ -> },
                onSetFallbackList = { _, listId -> selectedFallback = listId },
                onCopyScheduleScope = { _, _ -> }
            )
        }

        // Default rule card is displayed
        composeTestRule.onNodeWithTag("schedule_lazy_column")
            .performScrollToNode(hasTestTag("default_schedule_rule_card"))
        composeTestRule.onNodeWithTag("default_schedule_rule_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("DEFAULT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Always Valid (24/7)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("default_rule_active_now_badge").assertIsDisplayed()

        // Verify there is no delete button on default rule card
        composeTestRule.onNodeWithTag("rule_delete_default_rule_shared").assertDoesNotExist()

        // Change list via dropdown on default rule
        composeTestRule.onNodeWithTag("default_rule_list_dropdown_button").performClick()
        composeTestRule.onNodeWithTag("fallback_list_option_list_work").performClick()
        assertThat(selectedFallback).isEqualTo("list_work")
    }

    @Test
    fun `adding a rule allows selecting GPS rule with map view, current location button, and log distance slider`() {
        var savedRule: ScheduleRule? = null

        composeTestRule.setContent {
            RulesScreen(
                lists = listOf(defaultList, workList),
                isSeparateScreensMode = false,
                rulesShared = emptyList(),
                rulesHome = emptyList(),
                rulesLock = emptyList(),
                resolutionShared = defaultRes,
                resolutionHome = defaultRes.copy(screenScope = ScheduleScreenScope.HOME),
                resolutionLock = defaultRes.copy(screenScope = ScheduleScreenScope.LOCK),
                fallbackListIdShared = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdHome = WallpaperList.DEFAULT_LIST_ID,
                fallbackListIdLock = WallpaperList.DEFAULT_LIST_ID,
                onToggleSeparateScreensMode = {},
                onSaveRule = { savedRule = it },
                onDeleteRule = {},
                onToggleRuleEnabled = { _, _ -> },
                onMoveRulePriority = { _, _, _ -> },
                onSetFallbackList = { _, _ -> },
                onCopyScheduleScope = { _, _ -> }
            )
        }

        // Open Add Rule dialog
        composeTestRule.onNodeWithTag("add_schedule_rule_button").performClick()
        composeTestRule.onNodeWithTag("schedule_rule_editor_dialog").assertIsDisplayed()

        // Horizontal two option selector exists: Time Based (default on left) and GPS
        composeTestRule.onNodeWithTag("rule_type_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rule_type_time").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rule_type_gps").assertIsDisplayed()

        // Switch to GPS based rule
        composeTestRule.onNodeWithTag("rule_type_gps").performClick()

        // Map view is displayed in dialog hierarchy
        composeTestRule.onNodeWithTag("rule_map_view").assertExists()

        // Current location button is present
        composeTestRule.onNodeWithTag("rule_my_location_button").assertExists()

        // Distance slider in log scale is displayed
        composeTestRule.onNodeWithTag("rule_distance_slider").assertExists()
        composeTestRule.onNodeWithTag("rule_radius_label").assertExists()

        // Save rule
        composeTestRule.onNodeWithTag("save_schedule_rule_button").performClick()
        composeTestRule.onNodeWithTag("schedule_rule_editor_dialog").assertDoesNotExist()

        assertThat(savedRule).isNotNull()
        assertThat(savedRule!!.ruleType.name).isEqualTo("GPS")
        assertThat(savedRule!!.latitude).isNotNull()
        assertThat(savedRule!!.longitude).isNotNull()
        assertThat(savedRule!!.radiusMeters).isNotNull()
        assertThat(savedRule!!.radiusMeters).isGreaterThan(0.0)
    }
}
