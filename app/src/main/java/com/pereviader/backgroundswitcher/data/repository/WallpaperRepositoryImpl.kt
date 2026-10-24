package com.pereviader.backgroundswitcher.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ErrorResult
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
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val photoDao: PhotoDao,
    private val wallpaperCustomizationDao: WallpaperCustomizationDao,
    private val wallpaperManagerHelper: WallpaperManagerHelper,
    private val imageLoader: ImageLoader
) : WallpaperRepository {

    companion object {
        private const val TAG = "WallpaperRepository"
    }

    override fun getSettings(): Flow<WallpaperSettings> = preferencesManager.settingsFlow

    override suspend fun updateSettings(settings: WallpaperSettings) {
        preferencesManager.updateIntervalSeconds(settings.intervalSeconds)
        preferencesManager.updateTargetScreen(settings.targetScreen)
        preferencesManager.updateRotationOrder(settings.rotationOrder)
        preferencesManager.updateOnlyOnWifi(settings.onlyOnWifi)
        preferencesManager.updateIsEnabled(settings.isEnabled)
        preferencesManager.updateCycleOnUnlock(settings.cycleOnUnlock)
        preferencesManager.updateDoubleTapToCycle(settings.doubleTapToCycle)
    }

    override fun isLiveWallpaperActive(): Boolean = wallpaperManagerHelper.isLiveWallpaperActive()

    override suspend fun getNextCandidatePhoto(): PhotoItem? = withContext(Dispatchers.IO) {
        val settings = preferencesManager.settingsFlow.first()
        val entity = when (settings.rotationOrder) {
            RotationOrder.RANDOM -> photoDao.getRandomPhoto()
            RotationOrder.CHRONOLOGICAL -> {
                val currentPhoto = settings.currentPhotoId?.let { photoDao.getPhotoById(it) }
                    ?: settings.currentWallpaperUri?.let { photoDao.getPhotoByUri(it) }
                if (currentPhoto != null) {
                    photoDao.getNextChronologicalPhoto(currentPhoto.dateAdded) ?: photoDao.getOldestPhoto()
                } else {
                    photoDao.getOldestPhoto() ?: photoDao.getLatestPhoto()
                }
            }
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
                lastDisplayedAt = it.lastDisplayedAt,
                cropX = it.cropX,
                cropY = it.cropY,
                zoom = it.zoom,
                isSelected = it.isSelected
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
                .size(coil.size.Size.ORIGINAL)
                .allowHardware(false) // Software bitmap required for Canvas manipulation
                .build()

            val result = imageLoader.execute(request)
            if (result !is SuccessResult) {
                val error = (result as? ErrorResult)?.throwable
                Log.e(TAG, "Failed to load image from ${photo.uri}", error)
                // If the image is no longer accessible, clean it up and remove its customization
                if (!isPhotoReferenceable(photo.uri)) {
                    deleteUnreferenceablePhoto(photo.id, photo.uri)
                }
                return@withContext null
            }

            val sourceBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                ?: result.drawable.toBitmap()

            CenterCropEngine.createCoverBitmap(
                source = sourceBitmap,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                cropX = photo.cropX,
                cropY = photo.cropY,
                zoom = photo.zoom
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadAndCropBitmap for ${photo.uri}", e)
            if (!isPhotoReferenceable(photo.uri)) {
                deleteUnreferenceablePhoto(photo.id, photo.uri)
            }
            null
        }
    }

    override suspend fun applyWallpaper(bitmap: Bitmap): Result<Unit> {
        if (wallpaperManagerHelper.isLiveWallpaperActive()) {
            return Result.success(Unit)
        }
        val settings = preferencesManager.settingsFlow.first()
        return wallpaperManagerHelper.setWallpaper(bitmap, settings.targetScreen)
    }

    override suspend fun markPhotoDisplayed(photoId: String) {
        val now = System.currentTimeMillis()
        photoDao.updateLastDisplayed(photoId, now)
        val photo = photoDao.getPhotoById(photoId)
        preferencesManager.updateLastRotated(now, photo?.uri, photoId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getCurrentPhoto(): Flow<PhotoItem?> = preferencesManager.settingsFlow.flatMapLatest { settings ->
        when {
            settings.currentPhotoId != null -> photoDao.observePhotoById(settings.currentPhotoId)
            settings.currentWallpaperUri != null -> photoDao.observePhotoByUri(settings.currentWallpaperUri)
            else -> flowOf(null)
        }
    }.map { entity ->
        entity?.let {
            PhotoItem(
                id = it.id,
                albumId = it.albumId,
                uri = it.uri,
                width = it.width,
                height = it.height,
                dateAdded = it.dateAdded,
                lastDisplayedAt = it.lastDisplayedAt,
                cropX = it.cropX,
                cropY = it.cropY,
                zoom = it.zoom,
                isSelected = it.isSelected
            )
        }
    }

    override suspend fun updatePhotoCrop(photoId: String, cropX: Float?, cropY: Float?) {
        updatePhotoCustomization(photoId, cropX, cropY, null)
    }

    override suspend fun togglePhotoSelection(photoId: String, isSelected: Boolean) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoById(photoId) ?: return@withContext
        photoDao.updatePhotoSelection(photoId, isSelected)
        val uri = photo.uri
        val customization = wallpaperCustomizationDao.getCustomization(uri)
        val cropX = customization?.cropX ?: photo.cropX
        val cropY = customization?.cropY ?: photo.cropY
        val zoom = customization?.zoom ?: photo.zoom

        wallpaperCustomizationDao.insertOrUpdate(
            WallpaperCustomizationEntity(
                imageUri = uri,
                cropX = cropX,
                cropY = cropY,
                zoom = zoom,
                isSelected = isSelected,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun togglePhotoEnabled(photoId: String, isEnabled: Boolean) {
        togglePhotoSelection(photoId, isEnabled)
    }

    override suspend fun updatePhotoCustomization(
        photoId: String,
        cropX: Float?,
        cropY: Float?,
        zoom: Float?
    ) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoById(photoId)
        val uri = photo?.uri
        val currentSelected = photo?.isSelected ?: false

        if (uri != null) {
            wallpaperCustomizationDao.insertOrUpdate(
                WallpaperCustomizationEntity(
                    imageUri = uri,
                    cropX = cropX,
                    cropY = cropY,
                    zoom = zoom,
                    isSelected = currentSelected,
                    updatedAt = System.currentTimeMillis()
                )
            )
            photoDao.updateCustomizationByUri(uri, cropX, cropY, zoom)
        } else {
            photoDao.updateCustomization(photoId, cropX, cropY, zoom)
        }

        val settings = preferencesManager.settingsFlow.first()
        val isCurrent = settings.currentPhotoId == photoId || (uri != null && settings.currentWallpaperUri == uri)
        if (isCurrent) {
            reapplyCurrentWallpaper()
        }
        Unit
    }

    override suspend fun deleteUnreferenceablePhoto(photoId: String, uri: String) = withContext(Dispatchers.IO) {
        photoDao.deletePhotoById(photoId)
        wallpaperCustomizationDao.deleteOrphanedCustomizations()
        Unit
    }

    override suspend fun cleanupUnreferenceableCustomizations() = withContext(Dispatchers.IO) {
        val allPhotos = photoDao.getAllPhotos()
        val toDelete = allPhotos.filter { !isPhotoReferenceable(it.uri) }
        if (toDelete.isNotEmpty()) {
            photoDao.deletePhotosByIds(toDelete.map { it.id })
        }
        wallpaperCustomizationDao.deleteOrphanedCustomizations()
        Unit
    }

    override suspend fun reapplyCurrentWallpaper(): Result<Unit> = withContext(Dispatchers.IO) {
        if (wallpaperManagerHelper.isLiveWallpaperActive()) {
            val settings = preferencesManager.settingsFlow.first()
            val photoId = settings.currentPhotoId
            if (photoId != null) {
                markPhotoDisplayed(photoId)
            }
            return@withContext Result.success(Unit)
        }

        val settings = preferencesManager.settingsFlow.first()
        val entity = (settings.currentPhotoId?.let { photoDao.getPhotoById(it) }
            ?: settings.currentWallpaperUri?.let { photoDao.getPhotoByUri(it) })
            ?: return@withContext Result.failure(IllegalStateException("No current photo found to reapply"))

        val photoItem = PhotoItem(
            id = entity.id,
            albumId = entity.albumId,
            uri = entity.uri,
            width = entity.width,
            height = entity.height,
            dateAdded = entity.dateAdded,
            lastDisplayedAt = entity.lastDisplayedAt,
            cropX = entity.cropX,
            cropY = entity.cropY,
            zoom = entity.zoom,
            isSelected = entity.isSelected
        )

        val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
        val bitmap = loadAndCropBitmap(photoItem, dimensions.width, dimensions.height)
            ?: return@withContext Result.failure(IllegalStateException("Failed to render bitmap for wallpaper"))

        val result = applyWallpaper(bitmap)
        result
    }

    override suspend fun setPhotoAsWallpaper(photo: PhotoItem): Result<Unit> = withContext(Dispatchers.IO) {
        val freshest = photoDao.getPhotoById(photo.id)?.let {
            PhotoItem(
                id = it.id,
                albumId = it.albumId,
                uri = it.uri,
                width = it.width,
                height = it.height,
                dateAdded = it.dateAdded,
                lastDisplayedAt = it.lastDisplayedAt,
                cropX = it.cropX,
                cropY = it.cropY,
                zoom = it.zoom,
                isSelected = it.isSelected
            )
        } ?: photo

        val isLiveActive = wallpaperManagerHelper.isLiveWallpaperActive()
        val applyResult = if (isLiveActive) {
            // Live wallpaper is active: updating markPhotoDisplayed will emit to LiveWallpaperService
            // without calling setBitmap (which would cancel and unbind the live wallpaper).
            Result.success(Unit)
        } else {
            val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
            val bitmap = loadAndCropBitmap(freshest, dimensions.width, dimensions.height)
                ?: return@withContext Result.failure(IllegalStateException("Failed to load and crop photo: ${freshest.uri}"))
            applyWallpaper(bitmap)
        }

        if (applyResult.isSuccess) {
            markPhotoDisplayed(freshest.id)
        }
        applyResult
    }

    private fun isPhotoReferenceable(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "content" -> {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                }
                "file" -> {
                    File(uri.path ?: "").exists()
                }
                "http", "https" -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
