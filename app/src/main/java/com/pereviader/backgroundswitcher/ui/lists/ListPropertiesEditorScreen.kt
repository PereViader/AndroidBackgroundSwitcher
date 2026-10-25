package com.pereviader.backgroundswitcher.ui.lists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListPropertiesEditorScreen(
    list: WallpaperList,
    onSave: (name: String, targetScreen: TargetScreen, rotationOrder: RotationOrder) -> Unit,
    onBack: () -> Unit,
    onPreview: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var name by remember(list.name) { mutableStateOf(list.name) }
    var targetScreen by remember(list.targetScreen) { mutableStateOf(list.targetScreen) }
    var rotationOrder by remember(list.rotationOrder) { mutableStateOf(list.rotationOrder) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val suggestions = listOf("Work & Focus", "Night / Dark", "Family & Travel", "Weekends", "Lock Screen")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("list_properties_editor_screen")
    ) {
        // Top Bar with Back Button and Save Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("list_properties_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = if (list.isDefault) "Default List Properties" else "List Properties",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${list.photoCount} photos in this list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { onSave(name.trim(), targetScreen, rotationOrder) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .testTag("save_list_properties_button")
                        .testTag("confirm_list_dialog_button")
                ) {
                    Text("Save")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Full Screen Scrollable Form
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. List Name Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "List Name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("list_name_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { name = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // 2. Target Screen Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Screen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Choose which screen(s) will display wallpapers from this list",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TargetScreen.entries.forEach { screen ->
                        FilterChip(
                            selected = targetScreen == screen,
                            onClick = { targetScreen = screen },
                            label = {
                                Text(
                                    when (screen) {
                                        TargetScreen.HOME -> "Home"
                                        TargetScreen.LOCK -> "Lock"
                                        TargetScreen.BOTH -> "Both"
                                    }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("target_screen_chip_${screen.name.lowercase()}")
                        )
                    }
                }
            }

            // 3. Rotation Mode Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Rotation Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Choose how wallpapers in this list are ordered when cycling",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RotationOrder.entries.forEach { order ->
                        val isSelected = rotationOrder == order
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rotationOrder = order }
                                .testTag("rotation_mode_chip_${order.name.lowercase()}"),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (isSelected) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { rotationOrder = order }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when (order) {
                                            RotationOrder.RANDOM -> "Shuffle (Random)"
                                            RotationOrder.CHRONOLOGICAL -> "Chronological"
                                            RotationOrder.LEAST_RECENTLY_USED -> "Least Recently Used"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = when (order) {
                                            RotationOrder.RANDOM -> "Selects wallpapers randomly on each change"
                                            RotationOrder.CHRONOLOGICAL -> "Cycles in order of photo creation or import date"
                                            RotationOrder.LEAST_RECENTLY_USED -> "Cycles through every photo before repeating any"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Secondary Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("list_open_preview_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in Wallpaper Preview")
                }

                if (!list.isDefault) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("list_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete List")
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete '${list.name}'?") },
            text = {
                Text(
                    "Deleting this list removes its schedule rules and unassigns its ${list.photoCount} photo(s). " +
                        "Your original photos and crop customizations in Albums will not be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete_list_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
