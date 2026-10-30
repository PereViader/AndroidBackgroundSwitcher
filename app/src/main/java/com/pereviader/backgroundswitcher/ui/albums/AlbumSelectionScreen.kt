package com.pereviader.backgroundswitcher.ui.albums

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.ui.lists.AssignToListsDialog
import com.pereviader.backgroundswitcher.ui.lists.CreateOrEditListDialog
import com.pereviader.backgroundswitcher.ui.lists.ListPropertiesEditorScreen
import kotlinx.coroutines.flow.Flow

@Composable
fun AlbumSelectionScreen(
    albums: List<Album>,
    onToggleAlbum: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onImportGoogleDriveFolder: (Uri) -> Unit = {},
    onImportGooglePhotos: (List<Uri>) -> Unit = {},
    onSyncAlbum: (String) -> Unit = {},
    onDeleteAlbum: (String) -> Unit,
    getPhotosForAlbum: (String) -> Flow<List<PhotoItem>>,
    onTogglePhotoSelection: (photoId: String, isSelected: Boolean) -> Unit = { _, _ -> },
    onOpenAlbumInPreview: (albumId: String, photoId: String?) -> Unit = { _, _ -> },
    onImportGooglePhotosUrl: (String) -> Unit = {},
    onSyncGooglePhotosAlbum: (String) -> Unit = onSyncAlbum,
    lists: List<WallpaperList> = listOf(
        WallpaperList(
            id = WallpaperList.DEFAULT_LIST_ID,
            name = WallpaperList.DEFAULT_LIST_NAME,
            isDefault = true
        )
    ),
    workingListId: String = WallpaperList.DEFAULT_LIST_ID,
    onSelectWorkingList: (String) -> Unit = {},
    onSetPhotoLists: (String, Set<String>) -> Unit = { _, _ -> },
    onSetAlbumLists: (String, Set<String>) -> Unit = { _, _ -> },
    onCreateList: (String, (WallpaperList) -> Unit) -> Unit = { _, _ -> },
    onCreateListWithDetails: (String, TargetScreen, RotationOrder) -> Unit = { name, _, _ -> onCreateList(name) { _ -> } },
    onUpdateList: (listId: String, newName: String, targetScreen: TargetScreen, rotationOrder: RotationOrder) -> Unit = { _, _, _, _ -> },
    onDeleteList: (listId: String) -> Unit = {},
    onOpenListInPreview: (listId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var selectedAlbumForDetails by remember { mutableStateOf<Album?>(null) }
    var albumToDelete by remember { mutableStateOf<Album?>(null) }
    var photoToAssignLists by remember { mutableStateOf<PhotoItem?>(null) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var editingListId by rememberSaveable { mutableStateOf<String?>(null) }

    val editingList = lists.find { it.id == editingListId }

    if (editingList != null) {
        BackHandler(enabled = true) {
            editingListId = null
        }
        ListPropertiesEditorScreen(
            list = editingList,
            onSave = { newName, screen, order ->
                onUpdateList(editingList.id, newName, screen, order)
                editingListId = null
            },
            onBack = { editingListId = null },
            onPreview = {
                editingListId = null
                onOpenListInPreview(editingList.id)
            },
            onDelete = {
                onDeleteList(editingList.id)
                editingListId = null
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val driveFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: SecurityException) {
            }
            onImportGoogleDriveFolder(treeUri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportGooglePhotos(uris)
        }
    }

    val activeAlbum = selectedAlbumForDetails?.let { selected ->
        albums.find { it.id == selected.id } ?: selected
    }

    BackHandler(enabled = activeAlbum != null) {
        selectedAlbumForDetails = null
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (activeAlbum != null) {
            AlbumPhotosFullScreen(
                album = activeAlbum,
                photosFlow = getPhotosForAlbum(activeAlbum.id),
                lists = lists,
                workingListId = workingListId,
                onSelectWorkingList = onSelectWorkingList,
                onBack = { selectedAlbumForDetails = null },
                onSelectAll = { onToggleAlbum(activeAlbum.id, true) },
                onDeselectAll = { onToggleAlbum(activeAlbum.id, false) },
                onTogglePhotoSelection = onTogglePhotoSelection,
                onOpenAssignPhotoLists = { photo -> photoToAssignLists = photo },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Title at the top, matching Settings top position
                Text(
                    text = "Lists",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .testTag("albums_title")
                        .testTag("lists_title")
                )

                // Next line: Dropdown to select which list to edit + create list (+) + edit list (pencil) + refresh button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WorkingListDropdown(
                        lists = lists,
                        workingListId = workingListId,
                        onSelectWorkingList = onSelectWorkingList,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("working_list_dropdown")
                    )

                    IconButton(
                        onClick = { showCreateListDialog = true },
                        modifier = Modifier.testTag("create_list_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create New List")
                    }

                    IconButton(
                        onClick = { editingListId = workingListId },
                        modifier = Modifier
                            .testTag("edit_list_button")
                            .testTag("list_edit_$workingListId")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit List Properties")
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_albums_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh device albums")
                    }
                }

                if (albums.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No albums found.\nGrant media permissions, pick from Google Photos, or add a Google Drive folder.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onToggle = { isChecked -> onToggleAlbum(album.id, isChecked) },
                                onClick = { selectedAlbumForDetails = album },
                                onSync = { onSyncGooglePhotosAlbum(album.id) },
                                onDelete = { albumToDelete = album }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddSourceDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_album_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Album")
            }
        }

        if (showAddSourceDialog) {
            AddAlbumSourceDialog(
                onDismiss = { showAddSourceDialog = false },
                onSelectGoogleDrive = {
                    showAddSourceDialog = false
                    driveFolderLauncher.launch(null)
                },
                onSelectGooglePhotos = {
                    showAddSourceDialog = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }

        photoToAssignLists?.let { targetPhoto ->
            val initialIds = targetPhoto.assignedListIds.ifEmpty {
                if (targetPhoto.isSelected && workingListId == WallpaperList.DEFAULT_LIST_ID) {
                    setOf(workingListId)
                } else {
                    emptySet()
                }
            }
            AssignToListsDialog(
                title = "Assign Photo to Lists",
                subtitle = "Select which lists should include this wallpaper:",
                lists = lists,
                initialSelectedListIds = initialIds,
                onConfirm = { selectedListIds ->
                    onSetPhotoLists(targetPhoto.id, selectedListIds)
                },
                onCreateList = onCreateList,
                onDismiss = { photoToAssignLists = null }
            )
        }

        albumToDelete?.let { album ->
            AlertDialog(
                onDismissRequest = { albumToDelete = null },
                title = { Text("Delete Album") },
                text = { Text("Are you sure you want to remove '${album.name}' and all its imported photos from Background Switcher?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteAlbum(album.id)
                            albumToDelete = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { albumToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showCreateListDialog) {
            CreateOrEditListDialog(
                title = "Create New Photo List",
                initialName = "",
                confirmLabel = "Create",
                onConfirm = { name, targetScreen, rotationOrder ->
                    onCreateListWithDetails(name, targetScreen, rotationOrder)
                    showCreateListDialog = false
                },
                onDismiss = { showCreateListDialog = false }
            )
        }
    }
}

/**
 * Modern screen composable for the Lists tab (formerly AlbumSelectionScreen).
 */
@Composable
fun ListsScreen(
    albums: List<Album>,
    onToggleAlbum: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onImportGoogleDriveFolder: (Uri) -> Unit = {},
    onImportGooglePhotos: (List<Uri>) -> Unit = {},
    onSyncAlbum: (String) -> Unit = {},
    onDeleteAlbum: (String) -> Unit,
    getPhotosForAlbum: (String) -> Flow<List<PhotoItem>>,
    onTogglePhotoSelection: (photoId: String, isSelected: Boolean) -> Unit = { _, _ -> },
    onOpenAlbumInPreview: (albumId: String, photoId: String?) -> Unit = { _, _ -> },
    onImportGooglePhotosUrl: (String) -> Unit = {},
    onSyncGooglePhotosAlbum: (String) -> Unit = onSyncAlbum,
    lists: List<WallpaperList> = listOf(
        WallpaperList(
            id = WallpaperList.DEFAULT_LIST_ID,
            name = WallpaperList.DEFAULT_LIST_NAME,
            isDefault = true
        )
    ),
    workingListId: String = WallpaperList.DEFAULT_LIST_ID,
    onSelectWorkingList: (String) -> Unit = {},
    onSetPhotoLists: (String, Set<String>) -> Unit = { _, _ -> },
    onSetAlbumLists: (String, Set<String>) -> Unit = { _, _ -> },
    onCreateList: (String, (WallpaperList) -> Unit) -> Unit = { _, _ -> },
    onCreateListWithDetails: (String, TargetScreen, RotationOrder) -> Unit = { name, _, _ -> onCreateList(name) { _ -> } },
    onUpdateList: (listId: String, newName: String, targetScreen: TargetScreen, rotationOrder: RotationOrder) -> Unit = { _, _, _, _ -> },
    onDeleteList: (listId: String) -> Unit = {},
    onOpenListInPreview: (listId: String) -> Unit = {},
    modifier: Modifier = Modifier
) = AlbumSelectionScreen(
    albums = albums,
    onToggleAlbum = onToggleAlbum,
    onRefresh = onRefresh,
    onImportGoogleDriveFolder = onImportGoogleDriveFolder,
    onImportGooglePhotos = onImportGooglePhotos,
    onSyncAlbum = onSyncAlbum,
    onDeleteAlbum = onDeleteAlbum,
    getPhotosForAlbum = getPhotosForAlbum,
    onTogglePhotoSelection = onTogglePhotoSelection,
    onOpenAlbumInPreview = onOpenAlbumInPreview,
    onImportGooglePhotosUrl = onImportGooglePhotosUrl,
    onSyncGooglePhotosAlbum = onSyncGooglePhotosAlbum,
    lists = lists,
    workingListId = workingListId,
    onSelectWorkingList = onSelectWorkingList,
    onSetPhotoLists = onSetPhotoLists,
    onSetAlbumLists = onSetAlbumLists,
    onCreateList = onCreateList,
    onCreateListWithDetails = onCreateListWithDetails,
    onUpdateList = onUpdateList,
    onDeleteList = onDeleteList,
    onOpenListInPreview = onOpenListInPreview,
    modifier = modifier
)

@Composable
fun WorkingListDropdown(
    lists: List<WallpaperList>,
    workingListId: String,
    onSelectWorkingList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentList = lists.find { it.id == workingListId }
    val displayName = currentList?.name ?: lists.firstOrNull()?.name ?: WallpaperList.DEFAULT_LIST_NAME

    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .testTag("working_list_dropdown_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select List to Edit"
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("working_list_dropdown_menu")
        ) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = list.name,
                                fontWeight = if (list.id == workingListId) FontWeight.Bold else FontWeight.Normal
                            )
                            if (list.photoCount > 0) {
                                Text(
                                    text = "(${list.photoCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (list.isDefault) {
                                Text(
                                    text = "• Default",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelectWorkingList(list.id)
                        expanded = false
                    },
                    modifier = Modifier
                        .testTag("working_list_option_${list.id}")
                        .testTag("working_list_chip_${list.id}")
                )
            }
        }
    }
}

@Composable
private fun WorkingListSelectorBar(
    lists: List<WallpaperList>,
    workingListId: String,
    onSelectWorkingList: (String) -> Unit
) {
    WorkingListDropdown(
        lists = lists,
        workingListId = workingListId,
        onSelectWorkingList = onSelectWorkingList
    )
}

@Composable
private fun AddAlbumSourceDialog(
    onDismiss: () -> Unit,
    onSelectGoogleDrive: () -> Unit,
    onSelectGooglePhotos: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Wallpapers",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select wallpapers from your private Google account on this device:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Google Drive Folder
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectGoogleDrive() }
                        .testTag("add_google_drive_option"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add Google Drive Folder",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Choose any private Drive folder with automatic background sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Google Photos Picker
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectGooglePhotos() }
                        .testTag("pick_google_photos_option"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pick from Google Photos",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Select private photos directly from your Google Photos library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AlbumCard(
    album: Album,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val isGooglePhotos = album.source == AlbumSource.GOOGLE_PHOTOS_PICKER || album.source == AlbumSource.GOOGLE_PHOTOS_SHARE
    val isGoogleDrive = album.source == AlbumSource.GOOGLE_DRIVE_FOLDER
    val isCloudAlbum = isGooglePhotos || isGoogleDrive
    val sourceBadgeLabel = when (album.source) {
        AlbumSource.GOOGLE_DRIVE_FOLDER -> "Google Drive"
        AlbumSource.GOOGLE_PHOTOS_PICKER, AlbumSource.GOOGLE_PHOTOS_SHARE -> "Google Photos"
        else -> "Local"
    }
    val badgeIcon = when (album.source) {
        AlbumSource.GOOGLE_DRIVE_FOLDER -> Icons.Default.Cloud
        AlbumSource.GOOGLE_PHOTOS_PICKER, AlbumSource.GOOGLE_PHOTOS_SHARE -> Icons.Default.PhotoLibrary
        else -> Icons.Default.Folder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (album.isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = album.coverUri,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Checkbox pinned to top right
                Checkbox(
                    checked = album.isSelected,
                    onCheckedChange = { onToggle(it) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("album_checkbox_${album.id}")
                )

                // Source indicator badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .testTag("album_source_badge_${album.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sourceBadgeLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (album.selectedPhotoCount > 0) {
                        "${album.selectedPhotoCount} / ${album.photoCount} selected"
                    } else {
                        "${album.photoCount} photos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (isCloudAlbum) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!album.sourceUrl.isNullOrBlank()) {
                            IconButton(
                                onClick = onSync,
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("album_sync_${album.id}")
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "Sync Album",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("album_delete_${album.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Album",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumPhotosFullScreen(
    album: Album,
    photosFlow: Flow<List<PhotoItem>>,
    lists: List<WallpaperList>,
    workingListId: String,
    onSelectWorkingList: (String) -> Unit,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onTogglePhotoSelection: (photoId: String, isSelected: Boolean) -> Unit,
    onOpenAssignPhotoLists: (PhotoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val photos by photosFlow.collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("album_photos_fullscreen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("album_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onDeselectAll,
                    modifier = Modifier.testTag("deselect_all_button")
                ) {
                    Text("Deselect All")
                }

                Button(
                    onClick = onSelectAll,
                    modifier = Modifier.testTag("select_all_button")
                ) {
                    Text("Select All")
                }
            }
        }

        if (lists.size > 1) {
            WorkingListSelectorBar(
                lists = lists,
                workingListId = workingListId,
                onSelectWorkingList = onSelectWorkingList
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No photos in this album",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("album_photos_grid")
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val isSelectedInWorkingList = if (photo.assignedListIds.isNotEmpty()) {
                            workingListId in photo.assignedListIds
                        } else {
                            if (workingListId == WallpaperList.DEFAULT_LIST_ID) photo.isSelected else false
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelectedInWorkingList) 2.5.dp else 1.dp,
                                    color = if (isSelectedInWorkingList) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .combinedClickable(
                                    onClick = { onTogglePhotoSelection(photo.id, !isSelectedInWorkingList) },
                                    onLongClick = { onOpenAssignPhotoLists(photo) }
                                )
                                .testTag("album_photo_${photo.id}")
                        ) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (!isSelectedInWorkingList) {
                                            alpha = 0.65f
                                        }
                                    }
                            )
                            Checkbox(
                                checked = isSelectedInWorkingList,
                                onCheckedChange = { isChecked ->
                                    onTogglePhotoSelection(photo.id, isChecked)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .testTag("photo_toggle_${photo.id}")
                            )

                            if (photo.assignedListIds.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .clickable { onOpenAssignPhotoLists(photo) }
                                        .testTag("photo_lists_badge_${photo.id}")
                                ) {
                                    Text(
                                        text = "${photo.assignedListIds.size} lists",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
