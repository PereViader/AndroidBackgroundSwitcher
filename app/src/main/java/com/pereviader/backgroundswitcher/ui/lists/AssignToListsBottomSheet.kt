package com.pereviader.backgroundswitcher.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.pereviader.backgroundswitcher.core.model.WallpaperList

@Composable
fun AssignToListsDialog(
    title: String,
    subtitle: String? = null,
    lists: List<WallpaperList>,
    initialSelectedListIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onCreateList: (String, (WallpaperList) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(initialSelectedListIds) {
        mutableStateOf(initialSelectedListIds)
    }
    var newListName by remember { mutableStateOf("") }
    var showNewListInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("assign_to_lists_dialog"),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(lists, key = { it.id }) { list ->
                        val isChecked = list.id in selectedIds
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChecked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isChecked) {
                                        selectedIds - list.id
                                    } else {
                                        selectedIds + list.id
                                    }
                                }
                                .testTag("assign_list_row_${list.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = list.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${list.photoCount} photos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + list.id
                                        } else {
                                            selectedIds - list.id
                                        }
                                    },
                                    modifier = Modifier.testTag("assign_list_checkbox_${list.id}")
                                )
                            }
                        }
                    }
                }

                if (showNewListInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            label = { Text("New list name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inline_new_list_input")
                        )
                        Button(
                            onClick = {
                                val trimmed = newListName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onCreateList(trimmed) { created ->
                                        selectedIds = selectedIds + created.id
                                    }
                                    newListName = ""
                                    showNewListInput = false
                                }
                            },
                            enabled = newListName.isNotBlank(),
                            modifier = Modifier.testTag("inline_create_list_confirm")
                        ) {
                            Text("Add")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showNewListInput = true },
                        modifier = Modifier.testTag("inline_create_list_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New List")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedIds)
                    onDismiss()
                },
                modifier = Modifier.testTag("assign_lists_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
