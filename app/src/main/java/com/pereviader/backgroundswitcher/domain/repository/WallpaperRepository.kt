package com.pereviader.backgroundswitcher.domain.repository

import android.graphics.Bitmap
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun getSettings(): Flow<WallpaperSettings>
    suspend fun updateSettings(settings: WallpaperSettings)
    suspend fun getNextCandidatePhoto(): PhotoItem?
    suspend fun loadAndCropBitmap(photo: PhotoItem, targetWidth: Int, targetHeight: Int): Bitmap?
    suspend fun applyWallpaper(bitmap: Bitmap): Result<Unit>
    suspend fun markPhotoDisplayed(photoId: String)
}
