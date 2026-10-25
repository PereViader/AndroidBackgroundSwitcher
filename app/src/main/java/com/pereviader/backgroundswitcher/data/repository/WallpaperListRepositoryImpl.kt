package com.pereviader.backgroundswitcher.data.repository

import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.data.local.datastore.PreferencesManager
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperListDao
import com.pereviader.backgroundswitcher.data.local.db.entity.ListPhotoCrossRef
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperListEntity
import com.pereviader.backgroundswitcher.domain.repository.WallpaperListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperListRepositoryImpl @Inject constructor(
    private val wallpaperListDao: WallpaperListDao,
    private val photoDao: PhotoDao,
    private val wallpaperCustomizationDao: WallpaperCustomizationDao,
    private val preferencesManager: PreferencesManager
) : WallpaperListRepository {

    override fun observeLists(): Flow<List<WallpaperList>> {
        return combine(
            wallpaperListDao.observeAllLists(),
            wallpaperListDao.observeAllCrossRefs().onStart { emit(emptyList()) },
            photoDao.observeAllPhotos().onStart { emit(emptyList()) }
        ) { listEntities, crossRefs, allPhotos ->
            val effectiveEntities = if (listEntities.none { it.id == WallpaperList.DEFAULT_LIST_ID || it.isDefault }) {
                listOf(
                    WallpaperListEntity(
                        id = WallpaperList.DEFAULT_LIST_ID,
                        name = WallpaperList.DEFAULT_LIST_NAME,
                        isDefault = true,
                        orderIndex = 0
                    )
                ) + listEntities
            } else {
                listEntities
            }

            val photosById = allPhotos.associateBy { it.id }
            val refsByList = crossRefs.groupBy { it.listId }

            effectiveEntities.map { entity ->
                val refs = refsByList[entity.id].orEmpty().sortedByDescending { it.addedAt }
                val validPhotos = refs.mapNotNull { photosById[it.photoId] }
                WallpaperList(
                    id = entity.id,
                    name = entity.name,
                    isDefault = entity.isDefault,
                    orderIndex = entity.orderIndex,
                    createdAt = entity.createdAt,
                    photoCount = validPhotos.size,
                    coverUris = validPhotos.take(4).map { it.uri },
                    targetScreen = parseTargetScreen(entity.targetScreen),
                    rotationOrder = parseRotationOrder(entity.rotationOrder)
                )
            }
        }
    }

    override suspend fun getLists(): List<WallpaperList> {
        ensureDefaultListExists()
        return observeLists().first()
    }

    override fun observePhotosInList(listId: String): Flow<List<PhotoItem>> {
        return combine(
            wallpaperListDao.observePhotosInList(listId),
            wallpaperListDao.observeAllCrossRefs().onStart { emit(emptyList()) }
        ) { photos, allRefs ->
            val listIdsByPhoto = allRefs.groupBy({ it.photoId }, { it.listId })
            photos.map { entity ->
                val assigned = listIdsByPhoto[entity.id]?.toSet() ?: setOf(listId)
                entity.toDomain(assigned)
            }
        }
    }

    override suspend fun getPhotosInList(listId: String): List<PhotoItem> {
        val entities = wallpaperListDao.getPhotosInList(listId)
        val allRefs = wallpaperListDao.getAllCrossRefs()
        val listIdsByPhoto = allRefs.groupBy({ it.photoId }, { it.listId })
        return entities.map { entity ->
            val assigned = listIdsByPhoto[entity.id]?.toSet() ?: setOf(listId)
            entity.toDomain(assigned)
        }
    }

    override suspend fun ensureDefaultListExists(): WallpaperList {
        val existing = wallpaperListDao.getListById(WallpaperList.DEFAULT_LIST_ID)
            ?: wallpaperListDao.getDefaultList()
        if (existing != null) {
            return WallpaperList(
                id = existing.id,
                name = existing.name,
                isDefault = existing.isDefault,
                orderIndex = existing.orderIndex,
                createdAt = existing.createdAt,
                photoCount = wallpaperListDao.getPhotosCountInList(existing.id),
                targetScreen = parseTargetScreen(existing.targetScreen),
                rotationOrder = parseRotationOrder(existing.rotationOrder)
            )
        }
        val entity = WallpaperListEntity(
            id = WallpaperList.DEFAULT_LIST_ID,
            name = WallpaperList.DEFAULT_LIST_NAME,
            isDefault = true,
            orderIndex = 0,
            createdAt = System.currentTimeMillis()
        )
        wallpaperListDao.insertListIgnore(entity)
        return WallpaperList(
            id = entity.id,
            name = entity.name,
            isDefault = true,
            orderIndex = 0,
            createdAt = entity.createdAt,
            photoCount = 0,
            targetScreen = parseTargetScreen(entity.targetScreen),
            rotationOrder = parseRotationOrder(entity.rotationOrder)
        )
    }

    override suspend fun createList(
        name: String,
        targetScreen: TargetScreen,
        rotationOrder: RotationOrder
    ): WallpaperList {
        ensureDefaultListExists()
        val trimmed = name.trim().ifEmpty { "New List" }
        val currentLists = wallpaperListDao.getAllLists()
        val nextOrder = (currentLists.maxOfOrNull { it.orderIndex } ?: 0) + 1
        val now = System.currentTimeMillis()
        val id = "list_${UUID.randomUUID()}"
        val entity = WallpaperListEntity(
            id = id,
            name = trimmed,
            isDefault = false,
            orderIndex = nextOrder,
            createdAt = now,
            targetScreen = targetScreen.name,
            rotationOrder = rotationOrder.name
        )
        wallpaperListDao.insertList(entity)
        return WallpaperList(
            id = entity.id,
            name = entity.name,
            isDefault = false,
            orderIndex = entity.orderIndex,
            createdAt = entity.createdAt,
            photoCount = 0,
            targetScreen = targetScreen,
            rotationOrder = rotationOrder
        )
    }

    override suspend fun updateList(
        listId: String,
        name: String,
        targetScreen: TargetScreen,
        rotationOrder: RotationOrder
    ) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            wallpaperListDao.updateListDetails(
                id = listId,
                name = trimmed,
                targetScreen = targetScreen.name,
                rotationOrder = rotationOrder.name
            )
        }
    }

    override suspend fun renameList(listId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            wallpaperListDao.renameList(listId, trimmed)
        }
    }

    private fun parseTargetScreen(raw: String?): TargetScreen =
        raw?.let { runCatching { TargetScreen.valueOf(it) }.getOrNull() } ?: TargetScreen.BOTH

    private fun parseRotationOrder(raw: String?): RotationOrder =
        raw?.let { runCatching { RotationOrder.valueOf(it) }.getOrNull() } ?: RotationOrder.LEAST_RECENTLY_USED

    override suspend fun deleteList(listId: String): Boolean {
        if (listId == WallpaperList.DEFAULT_LIST_ID) return false
        val list = wallpaperListDao.getListById(listId) ?: return false
        if (list.isDefault) return false

        val affectedPhotos = wallpaperListDao.getCrossRefsForList(listId).map { it.photoId }
        val deleted = wallpaperListDao.deleteListById(listId) > 0
        if (deleted) {
            val settings = preferencesManager.settingsFlow.first()
            if (settings.fallbackListIdShared == listId) {
                preferencesManager.updateFallbackListId(ScheduleScreenScope.SHARED, WallpaperList.DEFAULT_LIST_ID)
            }
            if (settings.fallbackListIdHome == listId) {
                preferencesManager.updateFallbackListId(ScheduleScreenScope.HOME, WallpaperList.DEFAULT_LIST_ID)
            }
            if (settings.fallbackListIdLock == listId) {
                preferencesManager.updateFallbackListId(ScheduleScreenScope.LOCK, WallpaperList.DEFAULT_LIST_ID)
            }
            syncPhotoSelectionFlags(affectedPhotos)
        }
        return deleted
    }

    override suspend fun assignPhotosToList(listId: String, photoIds: List<String>) {
        if (photoIds.isEmpty()) return
        ensureDefaultListExists()
        val now = System.currentTimeMillis()
        val refs = photoIds.mapIndexed { idx, photoId ->
            ListPhotoCrossRef(
                listId = listId,
                photoId = photoId,
                addedAt = now + idx
            )
        }
        wallpaperListDao.insertCrossRefs(refs)
        syncPhotoSelectionFlags(photoIds)
    }

    override suspend fun removePhotosFromList(listId: String, photoIds: List<String>) {
        if (photoIds.isEmpty()) return
        wallpaperListDao.deleteCrossRefsForPhotos(listId, photoIds)
        syncPhotoSelectionFlags(photoIds)
    }

    override suspend fun togglePhotoInList(listId: String, photoId: String, assigned: Boolean) {
        if (assigned) {
            assignPhotosToList(listId, listOf(photoId))
        } else {
            removePhotosFromList(listId, listOf(photoId))
        }
    }

    override suspend fun setPhotoLists(photoId: String, targetListIds: Set<String>) {
        ensureDefaultListExists()
        wallpaperListDao.deleteAllCrossRefsForPhoto(photoId)
        if (targetListIds.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val refs = targetListIds.map { listId ->
                ListPhotoCrossRef(listId = listId, photoId = photoId, addedAt = now)
            }
            wallpaperListDao.insertCrossRefs(refs)
        }
        syncPhotoSelectionFlags(listOf(photoId))
    }

    override suspend fun assignAlbumToList(albumId: String, listId: String) {
        val photos = photoDao.getPhotosListByAlbum(albumId)
        assignPhotosToList(listId, photos.map { it.id })
    }

    override suspend fun removeAlbumFromList(albumId: String, listId: String) {
        val photos = photoDao.getPhotosListByAlbum(albumId)
        removePhotosFromList(listId, photos.map { it.id })
    }

    override suspend fun setAlbumLists(albumId: String, targetListIds: Set<String>) {
        ensureDefaultListExists()
        val photos = photoDao.getPhotosListByAlbum(albumId)
        val photoIds = photos.map { it.id }
        if (photoIds.isEmpty()) return

        val allLists = wallpaperListDao.getAllLists()
        for (list in allLists) {
            if (list.id in targetListIds) {
                assignPhotosToList(list.id, photoIds)
            } else {
                wallpaperListDao.deleteCrossRefsForPhotos(list.id, photoIds)
            }
        }
        syncPhotoSelectionFlags(photoIds)
    }

    private suspend fun syncPhotoSelectionFlags(photoIds: List<String>) {
        for (photoId in photoIds.distinct()) {
            val photo = photoDao.getPhotoById(photoId) ?: continue
            val remainingLists = wallpaperListDao.getListIdsForPhoto(photoId)
            val isSelected = remainingLists.isNotEmpty()
            if (photo.isSelected != isSelected) {
                photoDao.updatePhotoSelection(photoId, isSelected)
            }
            val customization = wallpaperCustomizationDao.getCustomization(photo.uri)
            if (customization != null && customization.isSelected != isSelected) {
                wallpaperCustomizationDao.insertOrUpdate(
                    customization.copy(
                        isSelected = isSelected,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else if (customization == null && isSelected) {
                wallpaperCustomizationDao.insertOrUpdate(
                    WallpaperCustomizationEntity(
                        imageUri = photo.uri,
                        cropX = photo.cropX,
                        cropY = photo.cropY,
                        zoom = photo.zoom,
                        isSelected = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun PhotoEntity.toDomain(assignedListIds: Set<String>) = PhotoItem(
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
        isSelected = assignedListIds.isNotEmpty() || isSelected,
        assignedListIds = assignedListIds
    )
}
