package com.pereviader.backgroundswitcher.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import java.time.DayOfWeek
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRuleEditorDialog(
    screenScope: ScheduleScreenScope,
    lists: List<WallpaperList>,
    initialRule: ScheduleRule? = null,
    onSave: (ScheduleRule) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultListId = initialRule?.listId
        ?: lists.firstOrNull { !it.isDefault }?.id
        ?: lists.firstOrNull()?.id
        ?: WallpaperList.DEFAULT_LIST_ID

    var selectedListId by remember(initialRule) { mutableStateOf(defaultListId) }
    var startMinute by remember(initialRule) { mutableIntStateOf(initialRule?.startMinuteOfDay ?: (9 * 60)) }
    var endMinute by remember(initialRule) { mutableIntStateOf(initialRule?.endMinuteOfDay ?: (17 * 60)) }
    var daysMask by remember(initialRule) { mutableIntStateOf(initialRule?.daysOfWeekMask ?: ScheduleRule.ALL_DAYS_MASK) }

    var showListDropdown by remember { mutableStateOf(false) }
    var editingTimeForStart by remember { mutableStateOf<Boolean?>(null) }

    val selectedList = lists.find { it.id == selectedListId }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("schedule_rule_editor_dialog"),
        title = {
            Text(
                text = if (initialRule == null) "Add Schedule Rule" else "Edit Schedule Rule",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Target Photo List Picker
                Column {
                    Text(
                        text = "Target Photo List",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showListDropdown = true }
                                .testTag("rule_editor_list_selector"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedList?.let { "${it.name} (${it.photoCount} photos)" }
                                        ?: WallpaperList.DEFAULT_LIST_NAME,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = showListDropdown,
                            onDismissRequest = { showListDropdown = false }
                        ) {
                            lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text("${list.name} (${list.photoCount} photos)") },
                                    onClick = {
                                        selectedListId = list.id
                                        showListDropdown = false
                                    },
                                    modifier = Modifier.testTag("rule_editor_list_option_${list.id}")
                                )
                            }
                        }
                    }
                }

                // 2. Time Window Presets + Start / End Pickers
                Column {
                    Text(
                        text = "Time Window",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editingTimeForStart = true }
                                .testTag("rule_editor_start_time"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Start Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = formatMinuteOfDay(startMinute),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editingTimeForStart = false }
                                .testTag("rule_editor_end_time"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "End Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = formatMinuteOfDay(endMinute),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (startMinute > endMinute) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🌙 Overnight rule: spans past midnight into the next morning",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val timePresets = listOf(
                            Triple("Work (09:00–17:00)", 9 * 60, 17 * 60),
                            Triple("Evening (17:00–22:00)", 17 * 60, 22 * 60),
                            Triple("Night (22:00–07:00)", 22 * 60, 7 * 60),
                            Triple("All Day (00:00–24:00)", 0, 0)
                        )
                        timePresets.forEach { (label, presetStart, presetEnd) ->
                            FilterChip(
                                selected = startMinute == presetStart && endMinute == presetEnd,
                                onClick = {
                                    startMinute = presetStart
                                    endMinute = presetEnd
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // 3. Days of Week Selector
                Column {
                    Text(
                        text = "Active Days",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = daysMask == ScheduleRule.ALL_DAYS_MASK,
                            onClick = { daysMask = ScheduleRule.ALL_DAYS_MASK },
                            label = { Text("Every day", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("preset_every_day")
                        )
                        FilterChip(
                            selected = daysMask == ScheduleRule.WEEKDAYS_MASK,
                            onClick = { daysMask = ScheduleRule.WEEKDAYS_MASK },
                            label = { Text("Weekdays", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("preset_weekdays")
                        )
                        FilterChip(
                            selected = daysMask == ScheduleRule.WEEKENDS_MASK,
                            onClick = { daysMask = ScheduleRule.WEEKENDS_MASK },
                            label = { Text("Weekends", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("preset_weekends")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dayLabels = listOf(
                            DayOfWeek.MONDAY to "Mon",
                            DayOfWeek.TUESDAY to "Tue",
                            DayOfWeek.WEDNESDAY to "Wed",
                            DayOfWeek.THURSDAY to "Thu",
                            DayOfWeek.FRIDAY to "Fri",
                            DayOfWeek.SATURDAY to "Sat",
                            DayOfWeek.SUNDAY to "Sun"
                        )
                        dayLabels.forEach { (day, label) ->
                            val bit = ScheduleRule.dayBit(day)
                            val isSelected = (daysMask and bit) != 0
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val next = if (isSelected) daysMask and bit.inv() else daysMask or bit
                                    if (next != 0) {
                                        daysMask = next
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("day_chip_${day.name}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rule = ScheduleRule(
                        id = initialRule?.id ?: "rule_${UUID.randomUUID()}",
                        listId = selectedListId,
                        listName = selectedList?.name ?: WallpaperList.DEFAULT_LIST_NAME,
                        screenScope = screenScope,
                        priority = initialRule?.priority ?: 999,
                        startMinuteOfDay = startMinute,
                        endMinuteOfDay = endMinute,
                        daysOfWeekMask = daysMask,
                        isEnabled = initialRule?.isEnabled ?: true
                    )
                    onSave(rule)
                    onDismiss()
                },
                enabled = daysMask != 0 && lists.isNotEmpty(),
                modifier = Modifier.testTag("save_schedule_rule_button")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    editingTimeForStart?.let { isStart ->
        val currentMinutes = if (isStart) startMinute else endMinute
        val timePickerState = rememberTimePickerState(
            initialHour = (currentMinutes / 60).coerceIn(0, 23),
            initialMinute = (currentMinutes % 60).coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingTimeForStart = null },
            title = { Text(if (isStart) "Select Start Time" else "Select End Time") },
            text = {
                Surface {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pickedMinutes = timePickerState.hour * 60 + timePickerState.minute
                        if (isStart) {
                            startMinute = pickedMinutes
                        } else {
                            endMinute = pickedMinutes
                        }
                        editingTimeForStart = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTimeForStart = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hours = (minuteOfDay / 60) % 24
    val minutes = minuteOfDay % 60
    return "%02d:%02d".format(hours, minutes)
}

fun formatDaysMaskSummary(mask: Int): String {
    return when (mask) {
        ScheduleRule.ALL_DAYS_MASK -> "Every day"
        ScheduleRule.WEEKDAYS_MASK -> "Weekdays (Mon–Fri)"
        ScheduleRule.WEEKENDS_MASK -> "Weekends (Sat–Sun)"
        0 -> "No days"
        else -> {
            val labels = listOf(
                DayOfWeek.MONDAY to "Mon",
                DayOfWeek.TUESDAY to "Tue",
                DayOfWeek.WEDNESDAY to "Wed",
                DayOfWeek.THURSDAY to "Thu",
                DayOfWeek.FRIDAY to "Fri",
                DayOfWeek.SATURDAY to "Sat",
                DayOfWeek.SUNDAY to "Sun"
            )
            labels.filter { (day, _) -> (mask and ScheduleRule.dayBit(day)) != 0 }
                .joinToString(", ") { it.second }
        }
    }
}
