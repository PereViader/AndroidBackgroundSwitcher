package com.pereviader.backgroundswitcher.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.ui.schedule.ScheduleContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun RulesScreen(
    lists: List<WallpaperList>,
    isSeparateScreensMode: Boolean,
    rulesShared: List<ScheduleRule>,
    rulesHome: List<ScheduleRule>,
    rulesLock: List<ScheduleRule>,
    resolutionShared: ActiveScheduleResolution,
    resolutionHome: ActiveScheduleResolution,
    resolutionLock: ActiveScheduleResolution,
    fallbackListIdShared: String,
    fallbackListIdHome: String,
    fallbackListIdLock: String,
    onToggleSeparateScreensMode: (Boolean) -> Unit = {},
    onSaveRule: (ScheduleRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRuleEnabled: (String, Boolean) -> Unit,
    onMoveRulePriority: (ScheduleScreenScope, String, Boolean) -> Unit = { _, _, _ -> },
    onReorderRules: (ScheduleScreenScope, List<String>) -> Unit = { _, _ -> },
    onSetFallbackList: (ScheduleScreenScope, String) -> Unit,
    onCopyScheduleScope: (ScheduleScreenScope, ScheduleScreenScope) -> Unit,
    onRefreshClock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        while (true) {
            onRefreshClock()
            delay(30_000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("rules_screen")
            .testTag("lists_and_schedule_screen")
    ) {
        ScheduleContent(
            lists = lists,
            isSeparateScreensMode = isSeparateScreensMode,
            rulesShared = rulesShared,
            rulesHome = rulesHome,
            rulesLock = rulesLock,
            resolutionShared = resolutionShared,
            resolutionHome = resolutionHome,
            resolutionLock = resolutionLock,
            fallbackListIdShared = fallbackListIdShared,
            fallbackListIdHome = fallbackListIdHome,
            fallbackListIdLock = fallbackListIdLock,
            onToggleSeparateScreensMode = onToggleSeparateScreensMode,
            onSaveRule = onSaveRule,
            onDeleteRule = onDeleteRule,
            onToggleRuleEnabled = onToggleRuleEnabled,
            onMoveRulePriority = onMoveRulePriority,
            onReorderRules = onReorderRules,
            onSetFallbackList = onSetFallbackList,
            onCopyScheduleScope = onCopyScheduleScope,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Backward-compatibility wrapper for [RulesScreen].
 */
@Composable
fun ListsAndScheduleScreen(
    lists: List<WallpaperList>,
    isSeparateScreensMode: Boolean,
    rulesShared: List<ScheduleRule>,
    rulesHome: List<ScheduleRule>,
    rulesLock: List<ScheduleRule>,
    resolutionShared: ActiveScheduleResolution,
    resolutionHome: ActiveScheduleResolution,
    resolutionLock: ActiveScheduleResolution,
    fallbackListIdShared: String,
    fallbackListIdHome: String,
    fallbackListIdLock: String,
    observePhotosInList: (String) -> Flow<List<PhotoItem>> = { flowOf(emptyList()) },
    onCreateList: (String) -> Unit = {},
    onRenameList: (String, String) -> Unit = { _, _ -> },
    onDeleteList: (String) -> Unit = {},
    onRemovePhotoFromList: (String, String) -> Unit = { _, _ -> },
    onUpdateList: (String, String, TargetScreen, RotationOrder) -> Unit = { _, _, _, _ -> },
    onCreateListWithDetails: (String, TargetScreen, RotationOrder) -> Unit = { _, _, _ -> },
    onToggleSeparateScreensMode: (Boolean) -> Unit = {},
    onSaveRule: (ScheduleRule) -> Unit = {},
    onDeleteRule: (String) -> Unit = {},
    onToggleRuleEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onMoveRulePriority: (ScheduleScreenScope, String, Boolean) -> Unit = { _, _, _ -> },
    onReorderRules: (ScheduleScreenScope, List<String>) -> Unit = { _, _ -> },
    onSetFallbackList: (ScheduleScreenScope, String) -> Unit = { _, _ -> },
    onCopyScheduleScope: (ScheduleScreenScope, ScheduleScreenScope) -> Unit = { _, _ -> },
    onRefreshClock: () -> Unit = {},
    onOpenAddPhotosFromAlbums: (String) -> Unit = {},
    onOpenList: (String) -> Unit = onOpenAddPhotosFromAlbums,
    onOpenListInPreview: (String) -> Unit = {},
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    RulesScreen(
        lists = lists,
        isSeparateScreensMode = isSeparateScreensMode,
        rulesShared = rulesShared,
        rulesHome = rulesHome,
        rulesLock = rulesLock,
        resolutionShared = resolutionShared,
        resolutionHome = resolutionHome,
        resolutionLock = resolutionLock,
        fallbackListIdShared = fallbackListIdShared,
        fallbackListIdHome = fallbackListIdHome,
        fallbackListIdLock = fallbackListIdLock,
        onToggleSeparateScreensMode = onToggleSeparateScreensMode,
        onSaveRule = onSaveRule,
        onDeleteRule = onDeleteRule,
        onToggleRuleEnabled = onToggleRuleEnabled,
        onMoveRulePriority = onMoveRulePriority,
        onReorderRules = onReorderRules,
        onSetFallbackList = onSetFallbackList,
        onCopyScheduleScope = onCopyScheduleScope,
        onRefreshClock = onRefreshClock,
        modifier = modifier
    )
}

