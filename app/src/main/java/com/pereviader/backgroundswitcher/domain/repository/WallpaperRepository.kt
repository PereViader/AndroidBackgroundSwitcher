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
    suspend fun updatePhotoCrop(photoId: String, cropX: Float?, cropY: Float?)
    suspend fun updatePhotoCustomization(photoId: String, cropX: Float?, cropY: Float?, zoom: Float?)
    suspend fun togglePhotoEnabled(photoId: String, isEnabled: Boolean)
    suspend fun deleteUnreferenceablePhoto(photoId: String, uri: String)
    suspend fun cleanupUnreferenceableCustomizations()
    suspend fun reapplyCurrentWallpaper(): Result<Unit>
    suspend fun setPhotoAsWallpaper(photo: PhotoItem): Result<Unit>
    fun isLiveWallpaperActive(): Boolean
    fun getCurrentPhoto(): Flow<PhotoItem?>
}
