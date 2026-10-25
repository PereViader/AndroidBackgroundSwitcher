package com.pereviader.backgroundswitcher.domain.repository

import android.graphics.Bitmap
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun getSettings(): Flow<WallpaperSettings>
    suspend fun updateSettings(settings: WallpaperSettings)
    suspend fun getNextCandidatePhoto(): PhotoItem? = getNextCandidatePhoto(ScheduleScreenScope.SHARED)
    suspend fun getNextCandidatePhoto(screenScope: ScheduleScreenScope): PhotoItem?
    suspend fun getActiveListTargetScreen(screenScope: ScheduleScreenScope = ScheduleScreenScope.SHARED): TargetScreen = TargetScreen.BOTH
    suspend fun loadAndCropBitmap(photo: PhotoItem, targetWidth: Int, targetHeight: Int): Bitmap?
    suspend fun applyWallpaper(bitmap: Bitmap): Result<Unit>
    suspend fun applyWallpaper(bitmap: Bitmap, targetScreenOverride: TargetScreen): Result<Unit> = applyWallpaper(bitmap)
    suspend fun markPhotoDisplayed(photoId: String)
    suspend fun markPhotoDisplayed(photoId: String, screenScope: ScheduleScreenScope) = markPhotoDisplayed(photoId)
    suspend fun updatePhotoCrop(photoId: String, cropX: Float?, cropY: Float?)
    suspend fun updatePhotoCustomization(photoId: String, cropX: Float?, cropY: Float?, zoom: Float?)
    suspend fun togglePhotoSelection(photoId: String, isSelected: Boolean)
    suspend fun togglePhotoEnabled(photoId: String, isEnabled: Boolean) = togglePhotoSelection(photoId, isEnabled)
    suspend fun deleteUnreferenceablePhoto(photoId: String, uri: String)
    suspend fun cleanupUnreferenceableCustomizations()
    suspend fun reapplyCurrentWallpaper(): Result<Unit>
    suspend fun setPhotoAsWallpaper(photo: PhotoItem): Result<Unit>
    suspend fun setPhotoAsWallpaper(photo: PhotoItem, targetScreenOverride: TargetScreen?): Result<Unit> = setPhotoAsWallpaper(photo)
    fun isLiveWallpaperActive(): Boolean
    fun getCurrentPhoto(): Flow<PhotoItem?>
}
