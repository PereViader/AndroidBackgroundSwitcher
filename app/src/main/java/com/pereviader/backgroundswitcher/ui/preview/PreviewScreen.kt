package com.pereviader.backgroundswitcher.ui.preview

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pereviader.backgroundswitcher.core.crop.CropCalculation
import com.pereviader.backgroundswitcher.core.crop.ScreenDimensions
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import java.util.Locale
import kotlin.math.max

@Composable
fun PreviewScreen(
    settings: WallpaperSettings,
    currentPhoto: PhotoItem?,
    onTriggerCycleNow: () -> Unit,
    onCustomizationChanged: (photoId: String, cropX: Float?, cropY: Float?, zoom: Float?) -> Unit,
    onResetCustomization: (photoId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenDimensions = remember(context) { ScreenMetricsHelper.getScreenDimensions(context) }
    val screenAspectRatio = if (screenDimensions.height > 0) {
        screenDimensions.width.toFloat() / screenDimensions.height.toFloat()
    } else {
        9f / 16f
    }

    val hasCustomization = currentPhoto != null && (
        currentPhoto.cropX != null ||
        currentPhoto.cropY != null ||
        (currentPhoto.zoom != null && currentPhoto.zoom > 1.001f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Unified action buttons placed above the preview for consistent UX and maximum preview space
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { currentPhoto?.let { onResetCustomization(it.id) } },
                enabled = hasCustomization,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Discard",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }

            FilledTonalButton(
                onClick = onTriggerCycleNow,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Change Wallpaper",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }

        // Preview container matching the phone's screen aspect ratio, expanded as large as possible
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val heightFromWidth = maxWidth / screenAspectRatio
            val (actualWidth, actualHeight) = if (heightFromWidth <= maxHeight) {
                maxWidth to heightFromWidth
            } else {
                (maxHeight * screenAspectRatio) to maxHeight
            }

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
                        }
                    )
                } else {
                    NoWallpaperPlaceholder()
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

    Box(
        modifier = modifier.fillMaxSize(),
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
                    onCustomizationChanged = onCustomizationChanged
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
    modifier: Modifier = Modifier
) {
    val initialNormX = (photo.cropX ?: 0.5f).coerceIn(0f, 1f)
    val initialNormY = (photo.cropY ?: 0.5f).coerceIn(0f, 1f)
    val initialZoom = (photo.zoom ?: 1.0f).coerceIn(1.0f, 5.0f)

    var normX by remember(photo.id, photo.cropX) { mutableFloatStateOf(initialNormX) }
    var normY by remember(photo.id, photo.cropY) { mutableFloatStateOf(initialNormY) }
    var currentZoom by remember(photo.id, photo.zoom) { mutableFloatStateOf(initialZoom) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val previewWidth = constraints.maxWidth.toFloat()
        val previewHeight = constraints.maxHeight.toFloat()
        val scaleFactor = previewHeight / screenDimensions.height.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(photo.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomDelta = event.calculateZoom()
                            val panDelta = event.calculatePan()

                            if (zoomDelta != 1f || panDelta != Offset.Zero) {
                                if (zoomDelta != 1f) {
                                    currentZoom = (currentZoom * zoomDelta).coerceIn(1.0f, 5.0f)
                                }

                                val currentCalc = CropCalculation.calculateCoverCrop(
                                    sourceWidth = bitmap.width,
                                    sourceHeight = bitmap.height,
                                    targetWidth = screenDimensions.width,
                                    targetHeight = screenDimensions.height,
                                    cropX = normX,
                                    cropY = normY,
                                    zoom = currentZoom
                                )
                                val excessX = max(0, currentCalc.scaledWidth - screenDimensions.width)
                                val excessY = max(0, currentCalc.scaledHeight - screenDimensions.height)
                                val prevExcessX = excessX * scaleFactor
                                val prevExcessY = excessY * scaleFactor

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

                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        // Commit when gesture ends (all touches released)
                        val finalCalc = CropCalculation.calculateCoverCrop(
                            sourceWidth = bitmap.width,
                            sourceHeight = bitmap.height,
                            targetWidth = screenDimensions.width,
                            targetHeight = screenDimensions.height,
                            cropX = normX,
                            cropY = normY,
                            zoom = currentZoom
                        )
                        val finalExcessX = max(0, finalCalc.scaledWidth - screenDimensions.width)
                        val finalExcessY = max(0, finalCalc.scaledHeight - screenDimensions.height)

                        val finalCropX = if (finalExcessX > 0) normX else null
                        val finalCropY = if (finalExcessY > 0) normY else null
                        val finalZoom = if (currentZoom > 1.001f) currentZoom else null

                        onCustomizationChanged(finalCropX, finalCropY, finalZoom)
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

            // Status badge indicating position and zoom
            val isCustomZoom = currentZoom > 1.001f
            val isCustomPos = (photo.cropX != null || normX != 0.5f) || (photo.cropY != null || normY != 0.5f)
            val isCustom = isCustomZoom || isCustomPos || photo.zoom != null

            val badgeText = when {
                isCustomZoom -> String.format(Locale.US, "Custom Fit • %.1fx", currentZoom)
                isCustomPos -> "Custom Position"
                else -> "Centered Cover Fit"
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
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
                text = "No Wallpaper Set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Change Wallpaper above to start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
