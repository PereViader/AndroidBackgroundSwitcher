package com.pereviader.backgroundswitcher.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pereviader.backgroundswitcher.core.crop.CenterCropEngine
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.core.wallpaper.WallpaperManagerHelper
import com.pereviader.backgroundswitcher.data.local.datastore.PreferencesManager
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val photoDao: PhotoDao,
    private val wallpaperManagerHelper: WallpaperManagerHelper,
    private val imageLoader: ImageLoader
) : WallpaperRepository {

    companion object {
        private const val TAG = "WallpaperRepository"
    }

    override fun getSettings(): Flow<WallpaperSettings> = preferencesManager.settingsFlow

    override suspend fun updateSettings(settings: WallpaperSettings) {
        preferencesManager.updateInterval(settings.intervalMinutes)
        preferencesManager.updateTargetScreen(settings.targetScreen)
        preferencesManager.updateRotationOrder(settings.rotationOrder)
        preferencesManager.updateOnlyOnWifi(settings.onlyOnWifi)
        preferencesManager.updateIsEnabled(settings.isEnabled)
    }

    override suspend fun getNextCandidatePhoto(): PhotoItem? = withContext(Dispatchers.IO) {
        val settings = preferencesManager.settingsFlow.first()
        val entity = when (settings.rotationOrder) {
            RotationOrder.RANDOM -> photoDao.getRandomPhoto()
            RotationOrder.CHRONOLOGICAL -> photoDao.getLatestPhoto()
            RotationOrder.LEAST_RECENTLY_USED -> photoDao.getLeastRecentlyUsedPhoto()
        }

        entity?.let {
            PhotoItem(
                id = it.id,
                albumId = it.albumId,
                uri = it.uri,
                width = it.width,
                height = it.height,
                dateAdded = it.dateAdded,
                lastDisplayedAt = it.lastDisplayedAt
            )
        }
    }

    override suspend fun loadAndCropBitmap(
        photo: PhotoItem,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(photo.uri)
                .allowHardware(false) // Software bitmap required for Canvas manipulation
                .build()

            val result = imageLoader.execute(request)
            if (result !is SuccessResult) {
                val error = (result as? ErrorResult)?.throwable
                Log.e(TAG, "Failed to load image from ${photo.uri}", error)
                return@withContext null
            }

            val sourceBitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (sourceBitmap == null) {
                Log.e(TAG, "Loaded drawable is not a BitmapDrawable for ${photo.uri}")
                return@withContext null
            }

            CenterCropEngine.createCoverBitmap(
                source = sourceBitmap,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadAndCropBitmap for ${photo.uri}", e)
            null
        }
    }

    override suspend fun applyWallpaper(bitmap: Bitmap): Result<Unit> {
        val settings = preferencesManager.settingsFlow.first()
        return wallpaperManagerHelper.setWallpaper(bitmap, settings.targetScreen)
    }

    override suspend fun markPhotoDisplayed(photoId: String) {
        val now = System.currentTimeMillis()
        photoDao.updateLastDisplayed(photoId, now)
        val photo = photoDao.getPhotoById(photoId)
        preferencesManager.updateLastRotated(now, photo?.uri)
    }
}
