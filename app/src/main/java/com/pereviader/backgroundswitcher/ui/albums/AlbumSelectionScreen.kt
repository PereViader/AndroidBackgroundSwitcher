package com.pereviader.backgroundswitcher.ui.albums

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
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
    onImportGooglePhotosUrl: (String) -> Unit,
    onSyncGooglePhotosAlbum: (String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    getPhotosForAlbum: (String) -> Flow<List<PhotoItem>>,
    onOpenAlbumInPreview: (albumId: String, photoId: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedAlbumForDetails by remember { mutableStateOf<Album?>(null) }
    var albumToDelete by remember { mutableStateOf<Album?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Album")
            }
        }
    ) { innerPadding ->
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
                        text = "No albums found.\nGrant media permissions or import a Google Photos album.",
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

        if (showImportDialog) {
            ImportGooglePhotosDialog(
                onDismiss = { showImportDialog = false },
                onImport = { url ->
                    showImportDialog = false
                    onImportGooglePhotosUrl(url)
                }
            )
        }

        selectedAlbumForDetails?.let { album ->
            AlbumPhotosDialog(
                album = album,
                photosFlow = getPhotosForAlbum(album.id),
                onDismiss = { selectedAlbumForDetails = null },
                onSync = { onSyncGooglePhotosAlbum(album.id) },
                onDelete = {
                    selectedAlbumForDetails = null
                    albumToDelete = album
                },
                onOpenInPreview = { photoId ->
                    selectedAlbumForDetails = null
                    onOpenAlbumInPreview(album.id, photoId)
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
private fun AlbumCard(
    album: Album,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val isGooglePhotos = album.source == AlbumSource.GOOGLE_PHOTOS_SHARE

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
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                // Source indicator badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isGooglePhotos) Icons.Default.Cloud else Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGooglePhotos) "Cloud" else "Local",
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
                    text = "${album.photoCount} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isGooglePhotos) {
                    Row {
                        IconButton(
                            onClick = onSync,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync Album",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
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
private fun AlbumPhotosDialog(
    album: Album,
    photosFlow: Flow<List<PhotoItem>>,
    onDismiss: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onOpenInPreview: (photoId: String?) -> Unit
) {
    val photos by photosFlow.collectAsState(initial = emptyList())
    val isGooglePhotos = album.source == AlbumSource.GOOGLE_PHOTOS_SHARE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isGooglePhotos) {
                            IconButton(onClick = onSync) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync from Google Photos")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${photos.size} photos • ${if (isGooglePhotos) "Google Photos" else "Device Album"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onOpenInPreview(null) }) {
                        Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview & Edit", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
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
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenInPreview(photo.id) }
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (!photo.isEnabled) {
                                                alpha = 0.45f
                                            }
                                        }
                                )
                                if (!photo.isEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.error
                                        ) {
                                            Text(
                                                text = "OFF",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onError,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            if (isGooglePhotos) {
                OutlinedButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    )
}

@Composable
private fun ImportGooglePhotosDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Google Photos Album") },
        text = {
            Column {
                Text(
                    text = "Tip: In the Google Photos app, tap Share on any album and choose 'Background Switcher', or paste its shared link below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Album Shared Link") },
                    placeholder = { Text("https://photos.app.goo.gl/...") },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.text?.let { clipText ->
                                url = clipText
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(url.trim()) },
                enabled = url.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
