package com.pereviader.backgroundswitcher.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.ui.preview.PreviewScreen

@Deprecated("Renamed to PreviewScreen", ReplaceWith("PreviewScreen"))
@Composable
fun DashboardScreen(
    settings: WallpaperSettings,
    currentPhoto: PhotoItem?,
    onTriggerCycleNow: () -> Unit,
    onCropChanged: (photoId: String, cropX: Float?, cropY: Float?) -> Unit,
    onResetCrop: (photoId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    PreviewScreen(
        albums = emptyList(),
        selectedAlbumId = null,
        photos = if (currentPhoto != null) listOf(currentPhoto) else emptyList(),
        currentIndex = 0,
        currentPhoto = currentPhoto,
        onSelectAlbum = {},
        onSelectPhotoIndex = {},
        onPreviousPhoto = {},
        onNextPhoto = {},
        onTogglePhotoEnabled = { _, _ -> },
        onCustomizationChanged = { photoId, cropX, cropY, _ ->
            onCropChanged(photoId, cropX, cropY)
        },
        onResetCustomization = onResetCrop,
        modifier = modifier
    )
}
