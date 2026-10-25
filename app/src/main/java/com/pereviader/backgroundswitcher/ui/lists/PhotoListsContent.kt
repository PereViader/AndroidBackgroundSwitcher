package com.pereviader.backgroundswitcher.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun PhotoListsContent(
    lists: List<WallpaperList>,
    isSeparateScreensMode: Boolean,
    activeSharedListId: String,
    activeHomeListId: String,
    activeLockListId: String,
    observePhotosInList: (String) -> Flow<List<PhotoItem>> = { flowOf(emptyList()) },
    onCreateList: (String) -> Unit,
    onRenameList: (String, String) -> Unit,
    onDeleteList: (String) -> Unit,
    onRemovePhotoFromList: (String, String) -> Unit = { _, _ -> },
    onUpdateList: (listId: String, name: String, targetScreen: TargetScreen, rotationOrder: RotationOrder) -> Unit = { id, name, _, _ -> onRenameList(id, name) },
    onCreateListWithDetails: (name: String, targetScreen: TargetScreen, rotationOrder: RotationOrder) -> Unit = { name, _, _ -> onCreateList(name) },
    onOpenAddPhotosFromAlbums: (String) -> Unit = {},
    onOpenList: (String) -> Unit = onOpenAddPhotosFromAlbums,
    onOpenListInPreview: (String) -> Unit = {},
    onEditList: ((WallpaperList) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<WallpaperList?>(null) }
    var listToDelete by remember { mutableStateOf<WallpaperList?>(null) }

    if (listToEdit != null && onEditList == null) {
        val target = listToEdit!!
        ListPropertiesEditorScreen(
            list = target,
            onSave = { newName, screen, order ->
                onUpdateList(target.id, newName, screen, order)
                listToEdit = null
            },
            onBack = { listToEdit = null },
            onPreview = {
                listToEdit = null
                onOpenListInPreview(target.id)
            },
            onDelete = {
                onDeleteList(target.id)
                listToEdit = null
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("photo_lists_content")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Photo Lists",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Tap a list to manage its photos in Albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_list_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New List")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("lists_grid")
        ) {
            items(lists, key = { it.id }) { list ->
                val activeBadge = resolveActiveBadgeLabel(
                    listId = list.id,
                    isSeparateScreensMode = isSeparateScreensMode,
                    activeSharedListId = activeSharedListId,
                    activeHomeListId = activeHomeListId,
                    activeLockListId = activeLockListId
                )
                WallpaperListCard(
                    wallpaperList = list,
                    activeBadgeLabel = activeBadge,
                    onClick = { onOpenList(list.id) },
                    onEdit = {
                        if (onEditList != null) {
                            onEditList(list)
                        } else {
                            listToEdit = list
                        }
                    },
                    onDelete = { listToDelete = list },
                    onPreview = { onOpenListInPreview(list.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditListDialog(
            title = "Create New Photo List",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { name, targetScreen, rotationOrder ->
                onCreateListWithDetails(name, targetScreen, rotationOrder)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    listToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Delete '${target.name}'?") },
            text = {
                Text(
                    "Deleting this list removes its schedule rules and unassigns its ${target.photoCount} photo(s). " +
                        "Your original photos and crop customizations in Albums will not be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteList(target.id)
                        listToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_list_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun resolveActiveBadgeLabel(
    listId: String,
    isSeparateScreensMode: Boolean,
    activeSharedListId: String,
    activeHomeListId: String,
    activeLockListId: String
): String? {
    return if (!isSeparateScreensMode) {
        if (listId == activeSharedListId) "ACTIVE NOW" else null
    } else {
        val isHome = listId == activeHomeListId
        val isLock = listId == activeLockListId
        when {
            isHome && isLock -> "ACTIVE • HOME & LOCK"
            isHome -> "ACTIVE • HOME"
            isLock -> "ACTIVE • LOCK"
            else -> null
        }
    }
}

@Composable
private fun WallpaperListCard(
    wallpaperList: WallpaperList,
    activeBadgeLabel: String?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit
) {
    val targetScreenLabel = when (wallpaperList.targetScreen) {
        TargetScreen.HOME -> "Home"
        TargetScreen.LOCK -> "Lock"
        TargetScreen.BOTH -> "Home & Lock"
    }
    val rotationOrderLabel = when (wallpaperList.rotationOrder) {
        RotationOrder.RANDOM -> "Shuffle"
        RotationOrder.CHRONOLOGICAL -> "Chronological"
        RotationOrder.LEAST_RECENTLY_USED -> "Least Recently Used"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("list_card_${wallpaperList.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activeBadgeLabel != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val covers = wallpaperList.coverUris
                when {
                    covers.size >= 4 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f)) {
                                AsyncImage(
                                    model = covers[0],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(1.dp)
                                )
                                AsyncImage(
                                    model = covers[1],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(1.dp)
                                )
                            }
                            Row(modifier = Modifier.weight(1f)) {
                                AsyncImage(
                                    model = covers[2],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(1.dp)
                                )
                                AsyncImage(
                                    model = covers[3],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(1.dp)
                                )
                            }
                        }
                    }
                    covers.isNotEmpty() -> {
                        AsyncImage(
                            model = covers.first(),
                            contentDescription = wallpaperList.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                if (activeBadgeLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .testTag("list_active_badge_${wallpaperList.id}")
                    ) {
                        Text(
                            text = activeBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wallpaperList.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${wallpaperList.photoCount} photos • $targetScreenLabel • $rotationOrderLabel" +
                            if (wallpaperList.isDefault) " • Default" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("list_edit_${wallpaperList.id}")
                        .testTag("list_menu_${wallpaperList.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit List Properties",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateOrEditListDialog(
    title: String,
    initialName: String,
    initialTargetScreen: TargetScreen = TargetScreen.BOTH,
    initialRotationOrder: RotationOrder = RotationOrder.LEAST_RECENTLY_USED,
    confirmLabel: String,
    onConfirm: (name: String, targetScreen: TargetScreen, rotationOrder: RotationOrder) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var targetScreen by remember(initialTargetScreen) { mutableStateOf(initialTargetScreen) }
    var rotationOrder by remember(initialRotationOrder) { mutableStateOf(initialRotationOrder) }
    val suggestions = listOf("Work & Focus", "Night / Dark", "Family & Travel", "Weekends", "Lock Screen")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("list_name_input")
                )
                if (initialName.isEmpty()) {
                    Text(
                        text = "Quick suggestions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Text(
                    text = "Target Screen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            modifier = Modifier.testTag("target_screen_chip_${screen.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Rotation Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RotationOrder.entries.forEach { order ->
                        FilterChip(
                            selected = rotationOrder == order,
                            onClick = { rotationOrder = order },
                            label = {
                                Text(
                                    when (order) {
                                        RotationOrder.RANDOM -> "Shuffle"
                                        RotationOrder.CHRONOLOGICAL -> "Chronological"
                                        RotationOrder.LEAST_RECENTLY_USED -> "Least Recently Used"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("rotation_mode_chip_${order.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), targetScreen, rotationOrder) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_list_dialog_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateOrRenameListDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    CreateOrEditListDialog(
        title = title,
        initialName = initialName,
        confirmLabel = confirmLabel,
        onConfirm = { name, _, _ -> onConfirm(name) },
        onDismiss = onDismiss
    )
}
