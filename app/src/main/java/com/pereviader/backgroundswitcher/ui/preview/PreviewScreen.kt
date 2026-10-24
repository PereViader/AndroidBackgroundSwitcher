package com.pereviader.backgroundswitcher.ui.preview

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pereviader.backgroundswitcher.core.crop.CropCalculation
import com.pereviader.backgroundswitcher.core.crop.ScreenDimensions
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

@Composable
fun PreviewScreen(
    photos: List<PhotoItem>,
    currentIndex: Int,
    currentPhoto: PhotoItem?,
    onSelectPhotoIndex: (Int) -> Unit,
    onPreviousPhoto: () -> Unit,
    onNextPhoto: () -> Unit,
    onTogglePhotoEnabled: (photoId: String, isEnabled: Boolean) -> Unit = { _, _ -> },
    onRemovePhoto: (photoId: String) -> Unit = { photoId -> onTogglePhotoEnabled(photoId, false) },
    onCustomizationChanged: (photoId: String, cropX: Float?, cropY: Float?, zoom: Float?) -> Unit,
    onSetAsWallpaper: (PhotoItem) -> Unit = {},
    isLiveWallpaperActive: Boolean = false,
    onActivateLiveWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenDimensions = remember(context) { ScreenMetricsHelper.getScreenDimensions(context) }
    val screenAspectRatio = if (screenDimensions.height > 0) {
        screenDimensions.width.toFloat() / screenDimensions.height.toFloat()
    } else {
        9f / 16f
    }

    var showActionModal by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLiveWallpaperActive) {
            Surface(
                onClick = onActivateLiveWallpaper,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Live Wallpaper for double-tap gestures & 10s rotation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Main Preview Card area: Maximized to take full height, zero clutter above
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val heightFromWidth = maxWidth / screenAspectRatio
            val (actualWidth, actualHeight) = if (heightFromWidth <= maxHeight) {
                maxWidth to heightFromWidth
            } else {
                (maxHeight * screenAspectRatio) to maxHeight
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    modifier = Modifier.size(width = actualWidth, height = actualHeight),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    if (currentPhoto != null) {
                        DraggablePhotoPreview(
                            photo = currentPhoto,
                            screenDimensions = screenDimensions,
                            onCustomizationChanged = { cropX, cropY, zoom ->
                                onCustomizationChanged(currentPhoto.id, cropX, cropY, zoom)
                            },
                            onLongPress = {
                                showActionModal = true
                            }
                        )
                    } else {
                        NoWallpaperPlaceholder()
                    }
                }

                // Left & Right Navigation Chevrons for fast 1-tap stepping through photos
                if (photos.size > 1) {
                    IconButton(
                        onClick = onPreviousPhoto,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 2.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Photo",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onNextPhoto,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Photo",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Quick Action Bar: Photo Counter
        if (currentPhoto != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentIndex + 1} of ${photos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bottom-Docked Controls: Filmstrip
        BottomBrowsingControls(
            photos = photos,
            currentIndex = currentIndex,
            onSelectPhotoIndex = onSelectPhotoIndex
        )
    }

    // Long-Press Quick Action Dialog
    if (showActionModal && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showActionModal = false },
            title = {
                Text(
                    text = "Photo Options",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            onRemovePhoto(currentPhoto.id)
                            showActionModal = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remove from Backgrounds"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BottomBrowsingControls(
    photos: List<PhotoItem>,
    currentIndex: Int,
    onSelectPhotoIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, photos.size) {
        if (photos.isNotEmpty() && currentIndex in photos.indices) {
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            if (itemInfo != null) {
                val viewportWidth = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                if (viewportWidth > 0) {
                    val itemCenter = itemInfo.offset + (itemInfo.size / 2)
                    val targetCenter = viewportWidth / 2
                    val delta = itemCenter - targetCenter
                    if (delta != 0) {
                        listState.animateScrollBy(delta.toFloat())
                    }
                }
            } else {
                listState.scrollToItem(currentIndex)
                kotlinx.coroutines.delay(50)
                val postItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
                if (postItem != null) {
                    val viewportWidth = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    if (viewportWidth > 0) {
                        val itemCenter = postItem.offset + (postItem.size / 2)
                        val targetCenter = viewportWidth / 2
                        val delta = itemCenter - targetCenter
                        if (delta != 0) {
                            listState.animateScrollBy(delta.toFloat())
                        }
                    }
                }
            }
        }
    }

    if (photos.isNotEmpty()) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                val isSelected = index == currentIndex
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectPhotoIndex(index) }
                        .testTag("filmstrip_item_${photo.id}")
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggablePhotoPreview(
    photo: PhotoItem,
    screenDimensions: ScreenDimensions,
    onCustomizationChanged: (cropX: Float?, cropY: Float?, zoom: Float?) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.uri) { mutableStateOf(true) }
    var isError by remember(photo.uri) { mutableStateOf(false) }

    LaunchedEffect(photo.uri) {
        isLoading = true
        isError = false
        try {
            val request = ImageRequest.Builder(context)
                .data(photo.uri)
                .size(coil.size.Size.ORIGINAL)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val loadedBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    ?: result.drawable.toBitmap()
                bitmap = loadedBitmap
                isLoading = false
            } else {
                isError = true
                isLoading = false
            }
        } catch (_: Exception) {
            isError = true
            isLoading = false
        }
    }

    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("photo_preview")
            .then(
                if (isLoading || isError || bitmap == null) {
                    Modifier.pointerInput(photo.id) {
                        detectTapGestures(onLongPress = { currentOnLongPress() })
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
            isError || bitmap == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Failed to load image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                PhotoPreviewCanvas(
                    bitmap = bitmap!!,
                    photo = photo,
                    screenDimensions = screenDimensions,
                    onCustomizationChanged = onCustomizationChanged,
                    onLongPress = onLongPress
                )
            }
        }
    }
}

@Composable
private fun PhotoPreviewCanvas(
    bitmap: Bitmap,
    photo: PhotoItem,
    screenDimensions: ScreenDimensions,
    onCustomizationChanged: (cropX: Float?, cropY: Float?, zoom: Float?) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialNormX = (photo.cropX ?: 0.5f).coerceIn(0f, 1f)
    val initialNormY = (photo.cropY ?: 0.5f).coerceIn(0f, 1f)
    val initialZoom = (photo.zoom ?: 1.0f).coerceIn(1.0f, 5.0f)

    var normX by remember(photo.id) { mutableFloatStateOf(initialNormX) }
    var normY by remember(photo.id) { mutableFloatStateOf(initialNormY) }
    var currentZoom by remember(photo.id) { mutableFloatStateOf(initialZoom) }

    val currentOnCustomizationChanged by rememberUpdatedState(onCustomizationChanged)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentBitmap by rememberUpdatedState(bitmap)
    val currentScreenDimensions by rememberUpdatedState(screenDimensions)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val previewWidth = constraints.maxWidth.toFloat()
        val previewHeight = constraints.maxHeight.toFloat()
        val scaleFactor = previewHeight / currentScreenDimensions.height.toFloat()
        val currentScaleFactor by rememberUpdatedState(scaleFactor)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(photo.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                        // Check if the gesture is a stationary hold (long press)
                        var initialPan = Offset.Zero
                        var initialZoom = 1f
                        var isFingerPressed = false
                        val movementOccurred = withTimeoutOrNull(longPressTimeout) {
                            var moved = false
                            do {
                                val checkEvent = awaitPointerEvent()
                                val pan = checkEvent.calculatePan()
                                val zoom = checkEvent.calculateZoom()
                                if (pan != Offset.Zero || zoom != 1f) {
                                    moved = true
                                    initialPan = pan
                                    initialZoom = zoom
                                    isFingerPressed = checkEvent.changes.any { it.pressed }
                                    checkEvent.changes.forEach { it.consume() }
                                    break
                                }
                            } while (checkEvent.changes.any { it.pressed })
                            moved
                        }

                        if (movementOccurred == null) {
                            // Timed out while holding still -> trigger long press
                            currentOnLongPress()
                            // Consume rest of touch until release
                            do {
                                val releaseEvent = awaitPointerEvent()
                                releaseEvent.changes.forEach { it.consume() }
                            } while (releaseEvent.changes.any { it.pressed })
                        } else if (movementOccurred) {
                            // Standard interactive pan & pinch-to-zoom loop
                            var anyMotion = false

                            fun applyMotion(panDelta: Offset, zoomDelta: Float) {
                                if (zoomDelta != 1f) {
                                    currentZoom = (currentZoom * zoomDelta).coerceIn(1.0f, 5.0f)
                                }

                                val currentCalc = CropCalculation.calculateCoverCrop(
                                    sourceWidth = currentBitmap.width,
                                    sourceHeight = currentBitmap.height,
                                    targetWidth = currentScreenDimensions.width,
                                    targetHeight = currentScreenDimensions.height,
                                    cropX = normX,
                                    cropY = normY,
                                    zoom = currentZoom
                                )
                                val excessX = max(0, currentCalc.scaledWidth - currentScreenDimensions.width)
                                val excessY = max(0, currentCalc.scaledHeight - currentScreenDimensions.height)
                                val prevExcessX = excessX * currentScaleFactor
                                val prevExcessY = excessY * currentScaleFactor

                                if (panDelta != Offset.Zero) {
                                    if (prevExcessX > 0f) {
                                        val deltaX = -panDelta.x / prevExcessX
                                        normX = (normX + deltaX).coerceIn(0f, 1f)
                                    } else {
                                        normX = 0.5f
                                    }
                                    if (prevExcessY > 0f) {
                                        val deltaY = -panDelta.y / prevExcessY
                                        normY = (normY + deltaY).coerceIn(0f, 1f)
                                    } else {
                                        normY = 0.5f
                                    }
                                } else {
                                    if (prevExcessX <= 0f) normX = 0.5f
                                    if (prevExcessY <= 0f) normY = 0.5f
                                }
                            }

                            if (initialPan != Offset.Zero || initialZoom != 1f) {
                                anyMotion = true
                                applyMotion(initialPan, initialZoom)
                            }

                            while (isFingerPressed) {
                                val event = awaitPointerEvent()
                                val zoomDelta = event.calculateZoom()
                                val panDelta = event.calculatePan()

                                if (zoomDelta != 1f || panDelta != Offset.Zero) {
                                    anyMotion = true
                                    applyMotion(panDelta, zoomDelta)
                                    event.changes.forEach { it.consume() }
                                }
                                isFingerPressed = event.changes.any { it.pressed }
                            }

                            if (anyMotion) {
                                currentOnCustomizationChanged(normX, normY, currentZoom)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val calc = CropCalculation.calculateCoverCrop(
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    targetWidth = screenDimensions.width,
                    targetHeight = screenDimensions.height,
                    cropX = normX,
                    cropY = normY,
                    zoom = currentZoom
                )

                val totalScale = calc.scale * scaleFactor
                val totalOffsetX = calc.offsetX * scaleFactor
                val totalOffsetY = calc.offsetY * scaleFactor

                val matrix = Matrix().apply {
                    postScale(totalScale, totalScale)
                    postTranslate(totalOffsetX, totalOffsetY)
                }

                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawBitmap(bitmap, matrix, paint)
                }
            }
        }
    }
}

@Composable
private fun NoWallpaperPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wallpaper,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No Photos to Display",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select albums in the Albums tab to preview photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
