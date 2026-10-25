package com.pereviader.backgroundswitcher.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.RuleType
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.WallpaperList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
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
    modifier: Modifier = Modifier
) {
    var selectedScreenTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Home, 1 = Lock
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<ScheduleRule?>(null) }

    val activeScope = when {
        !isSeparateScreensMode -> ScheduleScreenScope.SHARED
        selectedScreenTab == 0 -> ScheduleScreenScope.HOME
        else -> ScheduleScreenScope.LOCK
    }

    val currentRules = when (activeScope) {
        ScheduleScreenScope.SHARED -> rulesShared
        ScheduleScreenScope.HOME -> rulesHome
        ScheduleScreenScope.LOCK -> rulesLock
    }

    val currentResolution = when (activeScope) {
        ScheduleScreenScope.SHARED -> resolutionShared
        ScheduleScreenScope.HOME -> resolutionHome
        ScheduleScreenScope.LOCK -> resolutionLock
    }

    val currentFallbackListId = when (activeScope) {
        ScheduleScreenScope.SHARED -> fallbackListIdShared
        ScheduleScreenScope.HOME -> fallbackListIdHome
        ScheduleScreenScope.LOCK -> fallbackListIdLock
    }

    val customRules = currentRules.filter { !it.isDefaultRule && !it.id.startsWith("default_rule_") }
    val defaultRule = currentRules.find { it.isDefaultRule || it.id.startsWith("default_rule_") }
        ?: ScheduleRule.createDefaultRule(
            scope = activeScope,
            listId = currentFallbackListId,
            listName = lists.find { it.id == currentFallbackListId }?.name ?: WallpaperList.DEFAULT_LIST_NAME
        )

    var draggingRuleId by remember { mutableStateOf<String?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var localRules by remember(customRules) { mutableStateOf(customRules) }

    LaunchedEffect(customRules) {
        if (draggingRuleId == null) {
            localRules = customRules
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("schedule_content")
    ) {
        // Single unified header with ONE Add Rule button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Priority Schedule",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Evaluated top-to-bottom until a rule matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { showAddRuleDialog = true },
                modifier = Modifier.testTag("add_schedule_rule_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Rule")
            }
        }

        // Scope Tabs and Copy Action when Separate Mode is enabled in Settings
        if (isSeparateScreensMode) {
            PrimaryTabRow(
                selectedTabIndex = selectedScreenTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("separate_screen_tabs")
            ) {
                Tab(
                    selected = selectedScreenTab == 0,
                    onClick = { selectedScreenTab = 0 },
                    modifier = Modifier.testTag("schedule_tab_home"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Home Screen")
                        }
                    }
                )
                Tab(
                    selected = selectedScreenTab == 1,
                    onClick = { selectedScreenTab = 1 },
                    modifier = Modifier.testTag("schedule_tab_lock"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Screen")
                        }
                    }
                )
            }

            val copyFrom = if (selectedScreenTab == 0) ScheduleScreenScope.LOCK else ScheduleScreenScope.HOME
            val copyFromLabel = if (selectedScreenTab == 0) "Lock" else "Home"
            val copyToLabel = if (selectedScreenTab == 0) "Home" else "Lock"
            OutlinedButton(
                onClick = { onCopyScheduleScope(copyFrom, activeScope) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("copy_schedule_scope_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy $copyFromLabel schedule to $copyToLabel")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("schedule_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Custom Priority Rules List with Drag-to-Reorder
            itemsIndexed(localRules, key = { _, rule -> rule.id }) { index, rule ->
                val isMatchedNow = currentResolution.matchedRuleId == rule.id && !currentResolution.isFallback
                val resolvedListName = lists.find { it.id == rule.listId }?.name ?: rule.listName
                val isCurrentDragging = draggingRuleId == rule.id

                val dragModifier = Modifier.pointerInput(rule.id) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            draggingRuleId = rule.id
                            dragDeltaY = 0f
                        },
                        onDragEnd = {
                            val finalRules = localRules
                            val draggedId = draggingRuleId
                            draggingRuleId = null
                            dragDeltaY = 0f
                            if (draggedId != null) {
                                val oldIdx = customRules.indexOfFirst { it.id == draggedId }
                                val newIdx = finalRules.indexOfFirst { it.id == draggedId }
                                if (oldIdx != -1 && newIdx != -1 && oldIdx != newIdx) {
                                    val moveUp = newIdx < oldIdx
                                    onMoveRulePriority(activeScope, draggedId, moveUp)
                                    onReorderRules(activeScope, finalRules.map { it.id })
                                }
                            }
                        },
                        onDragCancel = {
                            draggingRuleId = null
                            dragDeltaY = 0f
                            localRules = customRules
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragDeltaY += dragAmount
                            val currentIndex = localRules.indexOfFirst { it.id == rule.id }
                            val thresholdPx = 10.dp.toPx()
                            if (currentIndex != -1) {
                                if (dragDeltaY > thresholdPx && currentIndex < localRules.lastIndex) {
                                    val mutable = localRules.toMutableList()
                                    val item = mutable.removeAt(currentIndex)
                                    mutable.add(currentIndex + 1, item)
                                    localRules = mutable
                                    dragDeltaY -= thresholdPx
                                } else if (dragDeltaY < -thresholdPx && currentIndex > 0) {
                                    val mutable = localRules.toMutableList()
                                    val item = mutable.removeAt(currentIndex)
                                    mutable.add(currentIndex - 1, item)
                                    localRules = mutable
                                    dragDeltaY += thresholdPx
                                }
                            }
                        }
                    )
                }

                ScheduleRuleCard(
                    rule = rule.copy(listName = resolvedListName),
                    rankNumber = index + 1,
                    isActiveNow = isMatchedNow,
                    onToggleEnabled = { enabled -> onToggleRuleEnabled(rule.id, enabled) },
                    onEdit = { ruleToEdit = rule },
                    onDelete = { onDeleteRule(rule.id) },
                    dragHandleModifier = dragModifier,
                    modifier = Modifier
                        .animateItem()
                        .then(
                            if (isCurrentDragging) {
                                Modifier
                                    .zIndex(10f)
                                    .graphicsLayer {
                                        translationY = dragDeltaY
                                        shadowElevation = 8.dp.toPx()
                                    }
                            } else {
                                Modifier
                            }
                        )
                )
            }

            // Unremovable Always-Valid Default Rule
            item(key = "default_rule_card") {
                val isDefaultActive = (currentResolution.matchedRuleId == defaultRule.id) ||
                    currentResolution.isFallback ||
                    currentResolution.matchedRuleId == null
                val defaultListName = lists.find { it.id == defaultRule.listId }?.name
                    ?: lists.find { it.id == currentFallbackListId }?.name
                    ?: WallpaperList.DEFAULT_LIST_NAME
                DefaultRuleCard(
                    rule = defaultRule.copy(listName = defaultListName),
                    lists = lists,
                    isActiveNow = isDefaultActive,
                    onSelectList = { listId ->
                        onSetFallbackList(activeScope, listId)
                        onSaveRule(defaultRule.copy(listId = listId, isDefaultRule = true))
                    }
                )
            }
        }
    }

    if (showAddRuleDialog) {
        ScheduleRuleEditorDialog(
            screenScope = activeScope,
            lists = lists,
            initialRule = null,
            onSave = onSaveRule,
            onDismiss = { showAddRuleDialog = false }
        )
    }

    ruleToEdit?.let { editing ->
        ScheduleRuleEditorDialog(
            screenScope = activeScope,
            lists = lists,
            initialRule = editing,
            onSave = onSaveRule,
            onDismiss = { ruleToEdit = null }
        )
    }
}

@Composable
private fun ScheduleRuleCard(
    rule: ScheduleRule,
    rankNumber: Int,
    isActiveNow: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isGps = rule.ruleType == RuleType.GPS
    val headerText = if (isGps) {
        "GPS • Within ${formatRadius(rule.radiusMeters ?: 100.0)}"
    } else if (rule.startMinuteOfDay == rule.endMinuteOfDay) {
        "All Day (24h)"
    } else {
        "${formatMinuteOfDay(rule.startMinuteOfDay)} – ${formatMinuteOfDay(rule.endMinuteOfDay)}"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isActiveNow) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .semantics {
                onClick(label = "Edit Rule") {
                    onEdit()
                    true
                }
            }
            .pointerInput(rule.id) {
                detectTapGestures(onTap = { onEdit() })
            }
            .testTag("schedule_rule_card_${rule.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveNow) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle on the left
            Box(
                modifier = dragHandleModifier
                    .size(36.dp)
                    .testTag("rule_drag_handle_${rule.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Main details Column
            Column(modifier = Modifier.weight(1f)) {
                // Top row: Rank, Active Badge, and Toggle Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "#$rankNumber",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (isActiveNow) {
                            ActiveRuleBadge(modifier = Modifier.testTag("rule_active_now_badge_${rule.id}"))
                        }
                    }

                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("rule_enable_switch_${rule.id}")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGps) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "List: ${rule.listName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isGps) {
                                "Target: ${"%.4f".format(rule.latitude ?: 0.0)}, ${"%.4f".format(rule.longitude ?: 0.0)}"
                            } else {
                                formatDaysMaskSummary(rule.daysOfWeekMask)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("rule_delete_${rule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Rule",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultRuleCard(
    rule: ScheduleRule,
    lists: List<WallpaperList>,
    isActiveNow: Boolean,
    onSelectList: (String) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val selectedList = lists.find { it.id == rule.listId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActiveNow) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .testTag("default_schedule_rule_card")
            .testTag("fallback_list_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveNow) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "DEFAULT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Always Valid (24/7)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isActiveNow) {
                        ActiveRuleBadge(
                            modifier = Modifier
                                .testTag("default_rule_active_now_badge")
                                .testTag("fallback_active_now_badge")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Unremovable base rule. Used automatically whenever no higher-priority schedule matches.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDropdown = true }
                        .testTag("default_rule_list_dropdown_button")
                        .testTag("fallback_list_dropdown_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "List:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedList?.let { "${it.name} (${it.photoCount} photos)" }
                                    ?: WallpaperList.DEFAULT_LIST_NAME,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Default List")
                    }
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    lists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text("${list.name} (${list.photoCount} photos)") },
                            onClick = {
                                onSelectList(list.id)
                                showDropdown = false
                            },
                            modifier = Modifier.testTag("fallback_list_option_${list.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackListCard(
    lists: List<WallpaperList>,
    selectedListId: String,
    isFallbackActiveNow: Boolean,
    onSelectFallbackList: (String) -> Unit
) {
    DefaultRuleCard(
        rule = ScheduleRule.createDefaultRule(
            scope = ScheduleScreenScope.SHARED,
            listId = selectedListId,
            listName = lists.find { it.id == selectedListId }?.name ?: WallpaperList.DEFAULT_LIST_NAME
        ),
        lists = lists,
        isActiveNow = isFallbackActiveNow,
        onSelectList = onSelectFallbackList
    )
}

@Composable
private fun ActiveRuleBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
            )
            Text(
                text = "ACTIVE NOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
