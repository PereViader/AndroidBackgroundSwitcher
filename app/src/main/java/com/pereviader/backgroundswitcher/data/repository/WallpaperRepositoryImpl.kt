package com.pereviader.backgroundswitcher.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pereviader.backgroundswitcher.core.crop.CenterCropEngine
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
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
    private val wallpaperManagerHelper: WallpaperManagerHelper
) : WallpaperRepository {

    private val imageLoader = ImageLoader.Builder(context).build()

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

            val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
            val sourceBitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext null

            CenterCropEngine.createCoverBitmap(
                source = sourceBitmap,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        } catch (_: Exception) {
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
        val photo = photoDao.getLeastRecentlyUsedPhoto()
        preferencesManager.updateLastRotated(now, photo?.uri)
    }
}
