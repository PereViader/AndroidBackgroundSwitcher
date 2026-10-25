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
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.core.wallpaper.WallpaperManagerHelper
import com.pereviader.backgroundswitcher.data.local.datastore.PreferencesManager
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperListDao
import com.pereviader.backgroundswitcher.data.local.db.entity.ListPhotoCrossRef
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperListEntity
import com.pereviader.backgroundswitcher.domain.repository.ScheduleRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val imageLoader: ImageLoader,
    private val wallpaperListDao: WallpaperListDao? = null,
    private val scheduleRepository: ScheduleRepository? = null
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
        preferencesManager.updateIsSeparateScreensMode(settings.isSeparateScreensMode)
        preferencesManager.updateFallbackListId(ScheduleScreenScope.SHARED, settings.fallbackListIdShared)
        preferencesManager.updateFallbackListId(ScheduleScreenScope.HOME, settings.fallbackListIdHome)
        preferencesManager.updateFallbackListId(ScheduleScreenScope.LOCK, settings.fallbackListIdLock)
    }

    override fun isLiveWallpaperActive(): Boolean = wallpaperManagerHelper.isLiveWallpaperActive()

    override suspend fun getNextCandidatePhoto(): PhotoItem? =
        getNextCandidatePhoto(ScheduleScreenScope.SHARED)

    override suspend fun getNextCandidatePhoto(screenScope: ScheduleScreenScope): PhotoItem? = withContext(Dispatchers.IO) {
        val settings = preferencesManager.settingsFlow.first()
        val effectiveScope = when {
            !settings.isSeparateScreensMode -> ScheduleScreenScope.SHARED
            screenScope == ScheduleScreenScope.SHARED -> ScheduleScreenScope.HOME
            else -> screenScope
        }

        val currentPhotoId = if (effectiveScope == ScheduleScreenScope.LOCK && settings.isSeparateScreensMode) {
            settings.currentLockPhotoId ?: settings.currentPhotoId
        } else {
            settings.currentPhotoId
        }
        val currentWallpaperUri = if (effectiveScope == ScheduleScreenScope.LOCK && settings.isSeparateScreensMode) {
            settings.currentLockWallpaperUri ?: settings.currentWallpaperUri
        } else {
            settings.currentWallpaperUri
        }

        val entity = resolveListScopedCandidate(
            settings = settings,
            effectiveScope = effectiveScope,
            currentPhotoId = currentPhotoId,
            currentWallpaperUri = currentWallpaperUri
        ) ?: resolveLegacyCandidate(
            settings = settings,
            currentPhotoId = currentPhotoId,
            currentWallpaperUri = currentWallpaperUri
        )

        entity?.toDomain()
    }

    private suspend fun resolveListScopedCandidate(
        settings: WallpaperSettings,
        effectiveScope: ScheduleScreenScope,
        currentPhotoId: String?,
        currentWallpaperUri: String?
    ): PhotoEntity? {
        val schedRepo = scheduleRepository ?: return null
        val listDao = wallpaperListDao ?: return null

        val resolution = schedRepo.resolveActiveList(effectiveScope)
        val fallbackId = when (effectiveScope) {
            ScheduleScreenScope.SHARED -> settings.fallbackListIdShared
            ScheduleScreenScope.HOME -> settings.fallbackListIdHome
            ScheduleScreenScope.LOCK -> settings.fallbackListIdLock
        }

        val candidateListIds = listOf(
            resolution.activeListId,
            fallbackId,
            WallpaperList.DEFAULT_LIST_ID
        ).distinct()

        for (listId in candidateListIds) {
            if (listDao.getPhotosCountInList(listId) > 0) {
                val listEntity = listDao.getListById(listId)
                val rotationOrder = listEntity?.let {
                    runCatching { RotationOrder.valueOf(it.rotationOrder) }.getOrNull()
                } ?: settings.rotationOrder
                val photo = queryPhotoInList(
                    listId = listId,
                    rotationOrder = rotationOrder,
                    currentPhotoId = currentPhotoId,
                    currentWallpaperUri = currentWallpaperUri
                )
                if (photo != null) return photo
            }
        }
        return null
    }

    private suspend fun queryPhotoInList(
        listId: String,
        rotationOrder: RotationOrder,
        currentPhotoId: String?,
        currentWallpaperUri: String?
    ): PhotoEntity? {
        return when (rotationOrder) {
            RotationOrder.RANDOM -> {
                if (currentPhotoId != null) {
                    photoDao.getRandomPhotoInListExcluding(listId, currentPhotoId)
                        ?: photoDao.getRandomPhotoInList(listId)
                } else {
                    photoDao.getRandomPhotoInList(listId)
                }
            }
            RotationOrder.CHRONOLOGICAL -> {
                val currentPhoto = currentPhotoId?.let { photoDao.getPhotoById(it) }
                    ?: currentWallpaperUri?.let { photoDao.getPhotoByUri(it) }
                if (currentPhoto != null) {
                    photoDao.getNextChronologicalPhotoInList(listId, currentPhoto.dateAdded)
                        ?: photoDao.getOldestPhotoInList(listId)
                } else {
                    photoDao.getOldestPhotoInList(listId) ?: photoDao.getLatestPhotoInList(listId)
                }
            }
            RotationOrder.LEAST_RECENTLY_USED -> {
                if (currentPhotoId != null) {
                    photoDao.getLeastRecentlyUsedPhotoInListExcluding(listId, currentPhotoId)
                        ?: photoDao.getLeastRecentlyUsedPhotoInList(listId)
                } else {
                    photoDao.getLeastRecentlyUsedPhotoInList(listId)
                }
            }
        }
    }

    private suspend fun resolveLegacyCandidate(
        settings: WallpaperSettings,
        currentPhotoId: String?,
        currentWallpaperUri: String?
    ): PhotoEntity? {
        return when (settings.rotationOrder) {
            RotationOrder.RANDOM -> {
                if (currentPhotoId != null) {
                    photoDao.getRandomPhotoExcluding(currentPhotoId) ?: photoDao.getRandomPhoto()
                } else {
                    photoDao.getRandomPhoto()
                }
            }
            RotationOrder.CHRONOLOGICAL -> {
                val currentPhoto = currentPhotoId?.let { photoDao.getPhotoById(it) }
                    ?: currentWallpaperUri?.let { photoDao.getPhotoByUri(it) }
                if (currentPhoto != null) {
                    photoDao.getNextChronologicalPhoto(currentPhoto.dateAdded) ?: photoDao.getOldestPhoto()
                } else {
                    photoDao.getOldestPhoto() ?: photoDao.getLatestPhoto()
                }
            }
            RotationOrder.LEAST_RECENTLY_USED -> {
                if (currentPhotoId != null) {
                    photoDao.getLeastRecentlyUsedPhotoExcluding(currentPhotoId) ?: photoDao.getLeastRecentlyUsedPhoto()
                } else {
                    photoDao.getLeastRecentlyUsedPhoto()
                }
            }
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

    override suspend fun getActiveListTargetScreen(screenScope: ScheduleScreenScope): TargetScreen = withContext(Dispatchers.IO) {
        val schedRepo = scheduleRepository
        val listDao = wallpaperListDao
        if (schedRepo != null && listDao != null) {
            val resolution = schedRepo.resolveActiveList(screenScope)
            val listEntity = listDao.getListById(resolution.activeListId)
            val targetScreen = listEntity?.let {
                runCatching { TargetScreen.valueOf(it.targetScreen) }.getOrNull()
            }
            if (targetScreen != null) return@withContext targetScreen
        }
        val settings = preferencesManager.settingsFlow.first()
        settings.targetScreen
    }

    override suspend fun applyWallpaper(bitmap: Bitmap): Result<Unit> {
        if (wallpaperManagerHelper.isLiveWallpaperActive()) {
            return Result.success(Unit)
        }
        val targetScreen = getActiveListTargetScreen(ScheduleScreenScope.SHARED)
        return wallpaperManagerHelper.setWallpaper(bitmap, targetScreen)
    }

    override suspend fun applyWallpaper(bitmap: Bitmap, targetScreenOverride: TargetScreen): Result<Unit> {
        if (wallpaperManagerHelper.isLiveWallpaperActive() && targetScreenOverride != TargetScreen.LOCK) {
            return Result.success(Unit)
        }
        return wallpaperManagerHelper.setWallpaper(bitmap, targetScreenOverride)
    }

    override suspend fun markPhotoDisplayed(photoId: String) =
        markPhotoDisplayed(photoId, ScheduleScreenScope.SHARED)

    override suspend fun markPhotoDisplayed(photoId: String, screenScope: ScheduleScreenScope) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val photo = photoDao.getPhotoById(photoId)
        if (screenScope == ScheduleScreenScope.LOCK) {
            preferencesManager.updateLastLockRotated(now, photo?.uri, photoId)
        } else {
            preferencesManager.updateLastRotated(now, photo?.uri, photoId)
        }
        photoDao.updateLastDisplayed(photoId, now)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getCurrentPhoto(): Flow<PhotoItem?> = preferencesManager.settingsFlow
        .map { it.currentPhotoId to it.currentWallpaperUri }
        .distinctUntilChanged()
        .flatMapLatest { (currentPhotoId, currentWallpaperUri) ->
            when {
                currentPhotoId != null -> photoDao.observePhotoById(currentPhotoId).distinctUntilChanged()
                currentWallpaperUri != null -> photoDao.observePhotoByUri(currentWallpaperUri).distinctUntilChanged()
                else -> flowOf(null)
            }
        }.map { entity ->
            entity?.toDomain()
        }
        .distinctUntilChanged()

    override suspend fun updatePhotoCrop(photoId: String, cropX: Float?, cropY: Float?) {
        updatePhotoCustomization(photoId, cropX, cropY, null)
    }

    override suspend fun togglePhotoSelection(photoId: String, isSelected: Boolean) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoById(photoId) ?: return@withContext
        photoDao.updatePhotoSelection(photoId, isSelected)
        wallpaperListDao?.let { listDao ->
            listDao.insertListIgnore(
                WallpaperListEntity(
                    id = WallpaperList.DEFAULT_LIST_ID,
                    name = WallpaperList.DEFAULT_LIST_NAME,
                    isDefault = true,
                    orderIndex = 0
                )
            )
            if (isSelected) {
                listDao.insertCrossRef(
                    ListPhotoCrossRef(
                        listId = WallpaperList.DEFAULT_LIST_ID,
                        photoId = photoId,
                        addedAt = System.currentTimeMillis()
                    )
                )
            } else {
                listDao.deleteAllCrossRefsForPhoto(photoId)
            }
        }
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

        val photoItem = entity.toDomain()

        val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
        val bitmap = loadAndCropBitmap(photoItem, dimensions.width, dimensions.height)
            ?: return@withContext Result.failure(IllegalStateException("Failed to render bitmap for wallpaper"))

        applyWallpaper(bitmap)
    }

    override suspend fun setPhotoAsWallpaper(photo: PhotoItem): Result<Unit> =
        setPhotoAsWallpaper(photo, targetScreenOverride = null)

    override suspend fun setPhotoAsWallpaper(
        photo: PhotoItem,
        targetScreenOverride: TargetScreen?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val freshest = photoDao.getPhotoById(photo.id)?.toDomain() ?: photo

        val isLiveActive = wallpaperManagerHelper.isLiveWallpaperActive()
        val applyResult = if (isLiveActive && targetScreenOverride != TargetScreen.LOCK) {
            Result.success(Unit)
        } else {
            val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
            val bitmap = loadAndCropBitmap(freshest, dimensions.width, dimensions.height)
                ?: return@withContext Result.failure(IllegalStateException("Failed to load and crop photo: ${freshest.uri}"))
            if (targetScreenOverride != null) {
                applyWallpaper(bitmap, targetScreenOverride)
            } else {
                applyWallpaper(bitmap)
            }
        }

        if (applyResult.isSuccess) {
            val scope = if (targetScreenOverride == TargetScreen.LOCK) {
                ScheduleScreenScope.LOCK
            } else {
                ScheduleScreenScope.SHARED
            }
            markPhotoDisplayed(freshest.id, scope)
        }
        applyResult
    }

    private fun PhotoEntity.toDomain() = PhotoItem(
        id = id,
        albumId = albumId,
        uri = uri,
        width = width,
        height = height,
        dateAdded = dateAdded,
        lastDisplayedAt = lastDisplayedAt,
        cropX = cropX,
        cropY = cropY,
        zoom = zoom,
        isSelected = isSelected
    )

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
