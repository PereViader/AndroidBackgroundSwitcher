package com.pereviader.backgroundswitcher.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    observePhotosInList: (String) -> Flow<List<PhotoItem>>,
    onCreateList: (String) -> Unit,
    onRenameList: (String, String) -> Unit,
    onDeleteList: (String) -> Unit,
    onRemovePhotoFromList: (String, String) -> Unit,
    onUpdateList: (String, String, TargetScreen, RotationOrder) -> Unit = { id, name, _, _ -> onRenameList(id, name) },
    onCreateListWithDetails: (String, TargetScreen, RotationOrder) -> Unit = { name, _, _ -> onCreateList(name) },
    onToggleSeparateScreensMode: (Boolean) -> Unit,
    onSaveRule: (ScheduleRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRuleEnabled: (String, Boolean) -> Unit,
    onMoveRulePriority: (ScheduleScreenScope, String, Boolean) -> Unit = { _, _, _ -> },
    onReorderRules: (ScheduleScreenScope, List<String>) -> Unit = { _, _ -> },
    onSetFallbackList: (ScheduleScreenScope, String) -> Unit,
    onCopyScheduleScope: (ScheduleScreenScope, ScheduleScreenScope) -> Unit,
    onRefreshClock: () -> Unit = {},
    onOpenAddPhotosFromAlbums: (String) -> Unit = {},
    onOpenList: (String) -> Unit = onOpenAddPhotosFromAlbums,
    onOpenListInPreview: (String) -> Unit = {},
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(initialSubTab) }

    LaunchedEffect(Unit) {
        while (true) {
            onRefreshClock()
            delay(30_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("lists_and_schedule_screen")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("lists_schedule_segmented_row"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentedTabButton(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    icon = Icons.Default.Collections,
                    label = "Photo Lists",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("segment_photo_lists")
                )
                SegmentedTabButton(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    icon = Icons.Default.Schedule,
                    label = "Priority Schedule",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("segment_priority_schedule")
                )
            }
        }

        if (selectedSubTab == 0) {
            PhotoListsContent(
                lists = lists,
                isSeparateScreensMode = isSeparateScreensMode,
                activeSharedListId = resolutionShared.activeListId,
                activeHomeListId = resolutionHome.activeListId,
                activeLockListId = resolutionLock.activeListId,
                observePhotosInList = observePhotosInList,
                onCreateList = onCreateList,
                onRenameList = onRenameList,
                onDeleteList = onDeleteList,
                onRemovePhotoFromList = onRemovePhotoFromList,
                onUpdateList = onUpdateList,
                onCreateListWithDetails = onCreateListWithDetails,
                onOpenAddPhotosFromAlbums = onOpenAddPhotosFromAlbums,
                onOpenList = onOpenList,
                onOpenListInPreview = onOpenListInPreview,
                modifier = Modifier.weight(1f)
            )
        } else {
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
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
