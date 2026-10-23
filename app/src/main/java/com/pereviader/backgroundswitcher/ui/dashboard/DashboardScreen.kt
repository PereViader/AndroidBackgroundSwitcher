package com.pereviader.backgroundswitcher.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import kotlin.math.max

@Composable
fun DashboardScreen(
    settings: WallpaperSettings,
    currentPhoto: PhotoItem?,
    onTriggerCycleNow: () -> Unit,
    onCropChanged: (photoId: String, cropX: Float?, cropY: Float?) -> Unit,
    onResetCrop: (photoId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenDimensions = remember(context) { ScreenMetricsHelper.getScreenDimensions(context) }
    val screenAspectRatio = if (screenDimensions.height > 0) {
        screenDimensions.width.toFloat() / screenDimensions.height.toFloat()
    } else {
        9f / 16f
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview container matching the phone's screen aspect ratio
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val cardMaxHeight = maxHeight * 0.94f
                val cardHeightFromWidth = maxWidth * 0.72f / screenAspectRatio
                val actualHeight = if (cardHeightFromWidth > cardMaxHeight) cardMaxHeight else cardHeightFromWidth
                val actualWidth = (actualHeight.value * screenAspectRatio).dp

                ElevatedCard(
                    modifier = Modifier.size(width = actualWidth, height = actualHeight),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    if (currentPhoto != null) {
                        DraggablePhotoPreview(
                            photo = currentPhoto,
                            onCropChanged = { cropX, cropY ->
                                onCropChanged(currentPhoto.id, cropX, cropY)
                            }
                        )
                    } else {
                        NoWallpaperPlaceholder()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action / status row below the preview card
            if (currentPhoto != null) {
                val hasCustomization = currentPhoto.cropX != null || currentPhoto.cropY != null
                if (hasCustomization) {
                    OutlinedButton(
                        onClick = { onResetCrop(currentPhoto.id) },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Discard customization",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Discard customization",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Drag image to customize position",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom space clearance so preview doesn't visually clash with the bottom-right button
            Spacer(modifier = Modifier.height(64.dp))
        }

        // Change Wallpaper button at the bottom right above the bottom menu
        ExtendedFloatingActionButton(
            onClick = onTriggerCycleNow,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(Icons.Default.Autorenew, contentDescription = "Change Wallpaper") },
            text = { Text("Change Wallpaper") }
        )
    }
}

@Composable
private fun DraggablePhotoPreview(
    photo: PhotoItem,
    onCropChanged: (cropX: Float?, cropY: Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(photo.uri)
            .size(coil.size.Size.ORIGINAL)
            .crossfade(true)
            .build()
    )

    val painterState = painter.state

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val targetWidth = constraints.maxWidth.toFloat()
        val targetHeight = constraints.maxHeight.toFloat()

        when (painterState) {
            is AsyncImagePainter.State.Loading,
            is AsyncImagePainter.State.Empty -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
            is AsyncImagePainter.State.Error -> {
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
            is AsyncImagePainter.State.Success -> {
                val intrinsicSize = painter.intrinsicSize
                val hasValidIntrinsic = intrinsicSize.isSpecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f
                val imgWidth = if (hasValidIntrinsic) {
                    intrinsicSize.width
                } else if (photo.width > 0) {
                    photo.width.toFloat()
                } else {
                    targetWidth
                }

                val imgHeight = if (hasValidIntrinsic) {
                    intrinsicSize.height
                } else if (photo.height > 0) {
                    photo.height.toFloat()
                } else {
                    targetHeight
                }

                val scaleX = targetWidth / imgWidth
                val scaleY = targetHeight / imgHeight
                val scale = max(scaleX, scaleY)

                val scaledWidth = imgWidth * scale
                val scaledHeight = imgHeight * scale

                val excessWidth = max(0f, scaledWidth - targetWidth)
                val excessHeight = max(0f, scaledHeight - targetHeight)

                // 0.5f represents center (default behavior)
                val initialNormX = (photo.cropX ?: 0.5f).coerceIn(0f, 1f)
                val initialNormY = (photo.cropY ?: 0.5f).coerceIn(0f, 1f)

                var offsetX by remember(photo.id, photo.cropX, excessWidth) {
                    mutableFloatStateOf(if (excessWidth > 0f) -excessWidth * initialNormX else 0f)
                }
                var offsetY by remember(photo.id, photo.cropY, excessHeight) {
                    mutableFloatStateOf(if (excessHeight > 0f) -excessHeight * initialNormY else 0f)
                }

                Box(
                    contentAlignment = Alignment.TopStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(photo.id, excessWidth, excessHeight) {
                            if (excessWidth > 0f || excessHeight > 0f) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (excessWidth > 0f) {
                                            offsetX = (offsetX + dragAmount.x).coerceIn(-excessWidth, 0f)
                                        }
                                        if (excessHeight > 0f) {
                                            offsetY = (offsetY + dragAmount.y).coerceIn(-excessHeight, 0f)
                                        }
                                    },
                                    onDragEnd = {
                                        val finalCropX = if (excessWidth > 0f) {
                                            (-offsetX / excessWidth).coerceIn(0f, 1f)
                                        } else null

                                        val finalCropY = if (excessHeight > 0f) {
                                            (-offsetY / excessHeight).coerceIn(0f, 1f)
                                        } else null

                                        onCropChanged(finalCropX, finalCropY)
                                    },
                                    onDragCancel = {
                                        val finalCropX = if (excessWidth > 0f) {
                                            (-offsetX / excessWidth).coerceIn(0f, 1f)
                                        } else null

                                        val finalCropY = if (excessHeight > 0f) {
                                            (-offsetY / excessHeight).coerceIn(0f, 1f)
                                        } else null

                                        onCropChanged(finalCropX, finalCropY)
                                    }
                                )
                            }
                        }
                ) {
                    Image(
                        painter = painter,
                        contentDescription = "Wallpaper Preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .requiredSize(
                                width = with(LocalDensity.current) { scaledWidth.toDp() },
                                height = with(LocalDensity.current) { scaledHeight.toDp() }
                            )
                            .graphicsLayer {
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )

                    // Positioning badge
                    val isCustom = photo.cropX != null || photo.cropY != null
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = if (isCustom) "Custom Position" else "Centered Cover Fit",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
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
                text = "No Wallpaper Set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Change Wallpaper below to start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
