package com.pereviader.backgroundswitcher.ui.albums

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var selectedAlbumForDetails by remember { mutableStateOf<Album?>(null) }
    var albumToDelete by remember { mutableStateOf<Album?>(null) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (activeAlbum == null) {
                FloatingActionButton(
                    onClick = { showAddSourceDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_album_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Album")
                }
            }
        }
    ) { innerPadding ->
        if (activeAlbum != null) {
            AlbumPhotosFullScreen(
                album = activeAlbum,
                photosFlow = getPhotosForAlbum(activeAlbum.id),
                onBack = { selectedAlbumForDetails = null },
                onSelectAll = { onToggleAlbum(activeAlbum.id, true) },
                onDeselectAll = { onToggleAlbum(activeAlbum.id, false) },
                onTogglePhotoSelection = onTogglePhotoSelection,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Header with Refresh Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Albums (${albums.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Tap an album to view photos • Checkbox selects for cycling",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
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
                        contentPadding = PaddingValues(16.dp),
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
    }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isCloudAlbum) {
                    Row {
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
                            Spacer(modifier = Modifier.width(4.dp))
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

@Composable
private fun AlbumPhotosFullScreen(
    album: Album,
    photosFlow: Flow<List<PhotoItem>>,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onTogglePhotoSelection: (photoId: String, isSelected: Boolean) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (photo.isSelected) 2.5.dp else 1.dp,
                                    color = if (photo.isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onTogglePhotoSelection(photo.id, !photo.isSelected) }
                                .testTag("album_photo_${photo.id}")
                        ) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (!photo.isSelected) {
                                            alpha = 0.65f
                                        }
                                    }
                            )
                            Checkbox(
                                checked = photo.isSelected,
                                onCheckedChange = { isChecked ->
                                    onTogglePhotoSelection(photo.id, isChecked)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .testTag("photo_toggle_${photo.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}
