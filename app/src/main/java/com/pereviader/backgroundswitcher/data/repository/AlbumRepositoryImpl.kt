package com.pereviader.backgroundswitcher.data.repository

import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.mediastore.MediaStoreAlbumScanner
import com.pereviader.backgroundswitcher.data.remote.GooglePhotosShareParser
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val photoDao: PhotoDao,
    private val wallpaperCustomizationDao: WallpaperCustomizationDao,
    private val mediaStoreScanner: MediaStoreAlbumScanner,
    private val googlePhotosParser: GooglePhotosShareParser
) : AlbumRepository {

    override fun getAlbums(): Flow<List<Album>> {
        return albumDao.getAllAlbums().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSelectedAlbums(): Flow<List<Album>> {
        return albumDao.getSelectedAlbums().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshDeviceAlbums() {
        val scannedAlbums = mediaStoreScanner.scanAlbums()
        val currentEntities = albumDao.getAllAlbums().first().associateBy { it.id }

        val entitiesToSave = scannedAlbums.map { scanned ->
            val existing = currentEntities[scanned.id]
            AlbumEntity(
                id = scanned.id,
                name = scanned.name,
                coverUri = scanned.coverUri,
                photoCount = scanned.photoCount,
                source = AlbumSource.LOCAL_MEDIASTORE.name,
                isSelected = existing?.isSelected ?: false,
                lastSyncedAt = System.currentTimeMillis(),
                sourceUrl = existing?.sourceUrl
            )
        }
        albumDao.insertOrUpdateAll(entitiesToSave)

        // For selected local albums, sync their photos
        val selectedAlbumIds = entitiesToSave.filter { it.isSelected }.map { it.id }
        for (albumId in selectedAlbumIds) {
            val scannedPhotos = mediaStoreScanner.getPhotosInAlbum(albumId)
            val existingDbPhotos = photoDao.getPhotosListByAlbum(albumId)
            val scannedIds = scannedPhotos.map { it.id }.toSet()

            // Delete photos from DB that are no longer in MediaStore (deleted from device)
            val toDelete = existingDbPhotos.filter { it.id !in scannedIds }
            if (toDelete.isNotEmpty()) {
                photoDao.deletePhotosByIds(toDelete.map { it.id })
            }

            val existingPhotosMap = existingDbPhotos.associateBy { it.id }
            val photosToSave = scannedPhotos.map { photo ->
                val existing = existingPhotosMap[photo.id]
                val customization = wallpaperCustomizationDao.getCustomization(photo.uri)
                photo.toEntity().copy(
                    cropX = customization?.cropX ?: existing?.cropX,
                    cropY = customization?.cropY ?: existing?.cropY,
                    zoom = customization?.zoom ?: existing?.zoom,
                    lastDisplayedAt = existing?.lastDisplayedAt ?: 0L,
                    isEnabled = customization?.isEnabled ?: existing?.isEnabled ?: true
                )
            }
            photoDao.insertOrUpdateAll(photosToSave)
        }

        // Clean up any customizations whose images no longer exist in any album
        wallpaperCustomizationDao.deleteOrphanedCustomizations()
    }

    override suspend fun toggleAlbumSelection(albumId: String, isSelected: Boolean) {
        albumDao.updateSelection(albumId, isSelected)
        if (isSelected) {
            val album = albumDao.getAlbumById(albumId)
            if (album != null && album.source == AlbumSource.LOCAL_MEDIASTORE.name) {
                val photos = mediaStoreScanner.getPhotosInAlbum(albumId)
                val existingPhotos = photoDao.getPhotosListByAlbum(albumId).associateBy { it.id }
                val photosToSave = photos.map { photo ->
                    val existing = existingPhotos[photo.id]
                    val customization = wallpaperCustomizationDao.getCustomization(photo.uri)
                    photo.toEntity().copy(
                        cropX = customization?.cropX ?: existing?.cropX,
                        cropY = customization?.cropY ?: existing?.cropY,
                        zoom = customization?.zoom ?: existing?.zoom,
                        lastDisplayedAt = existing?.lastDisplayedAt ?: 0L,
                        isEnabled = customization?.isEnabled ?: existing?.isEnabled ?: true
                    )
                }
                photoDao.insertOrUpdateAll(photosToSave)
            }
        }
    }

    override suspend fun importGooglePhotosAlbum(url: String): Result<Album> {
        val parseResult = googlePhotosParser.parseAlbumUrl(url)
        return parseResult.map { parsed ->
            val albumEntity = parsed.album.toEntity().copy(isSelected = true)
            albumDao.insertOrUpdate(albumEntity)
            photoDao.deletePhotosByAlbum(parsed.album.id)
            val photosToSave = parsed.photos.map { photo ->
                val customization = wallpaperCustomizationDao.getCustomization(photo.uri)
                photo.toEntity().copy(
                    cropX = customization?.cropX,
                    cropY = customization?.cropY,
                    zoom = customization?.zoom,
                    isEnabled = customization?.isEnabled ?: true
                )
            }
            photoDao.insertOrUpdateAll(photosToSave)
            wallpaperCustomizationDao.deleteOrphanedCustomizations()
            parsed.album.copy(isSelected = true)
        }
    }

    override suspend fun syncGooglePhotosAlbum(albumId: String): Result<Album> {
        val album = albumDao.getAlbumById(albumId)
            ?: return Result.failure(IllegalStateException("Album not found"))
        val url = album.sourceUrl
            ?: return Result.failure(IllegalStateException("Album has no remote Google Photos URL saved"))

        val parseResult = googlePhotosParser.parseAlbumUrl(url)
        return parseResult.map { parsed ->
            val updatedEntity = album.copy(
                name = parsed.album.name,
                coverUri = parsed.album.coverUri,
                photoCount = parsed.photos.size,
                lastSyncedAt = System.currentTimeMillis()
            )
            val existingPhotos = photoDao.getPhotosListByAlbum(albumId).associateBy { it.uri }
            val photosToSave = parsed.photos.map { photo ->
                val existing = existingPhotos[photo.uri]
                val customization = wallpaperCustomizationDao.getCustomization(photo.uri)
                photo.toEntity().copy(
                    cropX = customization?.cropX ?: existing?.cropX,
                    cropY = customization?.cropY ?: existing?.cropY,
                    zoom = customization?.zoom ?: existing?.zoom,
                    lastDisplayedAt = existing?.lastDisplayedAt ?: 0L,
                    isEnabled = customization?.isEnabled ?: existing?.isEnabled ?: true
                )
            }
            albumDao.insertOrUpdate(updatedEntity)
            photoDao.deletePhotosByAlbum(albumId)
            photoDao.insertOrUpdateAll(photosToSave)
            wallpaperCustomizationDao.deleteOrphanedCustomizations()
            updatedEntity.toDomain()
        }
    }

    override suspend fun syncAllSelectedAlbums() {
        val selected = albumDao.getSelectedAlbums().first()
        for (album in selected) {
            when (album.source) {
                AlbumSource.LOCAL_MEDIASTORE.name -> {
                    val scannedPhotos = mediaStoreScanner.getPhotosInAlbum(album.id)
                    val existingDbPhotos = photoDao.getPhotosListByAlbum(album.id)
                    val scannedIds = scannedPhotos.map { it.id }.toSet()

                    val toDelete = existingDbPhotos.filter { it.id !in scannedIds }
                    if (toDelete.isNotEmpty()) {
                        photoDao.deletePhotosByIds(toDelete.map { it.id })
                    }

                    val existingPhotosMap = existingDbPhotos.associateBy { it.id }
                    val photosToSave = scannedPhotos.map { photo ->
                        val existing = existingPhotosMap[photo.id]
                        val customization = wallpaperCustomizationDao.getCustomization(photo.uri)
                        photo.toEntity().copy(
                            cropX = customization?.cropX ?: existing?.cropX,
                            cropY = customization?.cropY ?: existing?.cropY,
                            zoom = customization?.zoom ?: existing?.zoom,
                            lastDisplayedAt = existing?.lastDisplayedAt ?: 0L,
                            isEnabled = customization?.isEnabled ?: existing?.isEnabled ?: true
                        )
                    }
                    photoDao.insertOrUpdateAll(photosToSave)
                    albumDao.insertOrUpdate(
                        album.copy(
                            photoCount = scannedPhotos.size,
                            coverUri = scannedPhotos.firstOrNull()?.uri ?: album.coverUri,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
                AlbumSource.GOOGLE_PHOTOS_SHARE.name -> {
                    if (!album.sourceUrl.isNullOrBlank()) {
                        syncGooglePhotosAlbum(album.id)
                    }
                }
            }
        }
        wallpaperCustomizationDao.deleteOrphanedCustomizations()
    }

    override fun getPhotosInAlbum(albumId: String): Flow<List<PhotoItem>> {
        return photoDao.getPhotosByAlbum(albumId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPhotosFromSelectedAlbums(): Flow<List<PhotoItem>> {
        return photoDao.getPhotosFromSelectedAlbums().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteAlbum(albumId: String) {
        albumDao.deleteAlbum(albumId)
        photoDao.deletePhotosByAlbum(albumId)
        wallpaperCustomizationDao.deleteOrphanedCustomizations()
    }

    override suspend fun importSharedPhotos(uris: List<String>, albumTitle: String?): Result<Album> {
        if (uris.isEmpty()) return Result.failure(IllegalArgumentException("No photos provided"))
        val albumId = "shared_direct"
        val existing = albumDao.getAlbumById(albumId)
        val name = albumTitle ?: "Shared Photos"
        val album = Album(
            id = albumId,
            name = name,
            coverUri = uris.firstOrNull(),
            photoCount = (existing?.photoCount ?: 0) + uris.size,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE,
            isSelected = true,
            lastSyncedAt = System.currentTimeMillis()
        )
        val photos = uris.mapIndexed { index, uri ->
            val customization = wallpaperCustomizationDao.getCustomization(uri)
            PhotoItem(
                id = "${albumId}_${System.currentTimeMillis()}_$index",
                albumId = albumId,
                uri = uri,
                width = 1920,
                height = 1080,
                dateAdded = System.currentTimeMillis() - (index * 1000L),
                lastDisplayedAt = 0L,
                cropX = customization?.cropX,
                cropY = customization?.cropY,
                zoom = customization?.zoom,
                isEnabled = customization?.isEnabled ?: true
            )
        }
        albumDao.insertOrUpdate(album.toEntity())
        photoDao.insertOrUpdateAll(photos.map { it.toEntity() })
        return Result.success(album)
    }

    private fun AlbumEntity.toDomain() = Album(
        id = id,
        name = name,
        coverUri = coverUri,
        photoCount = photoCount,
        source = runCatching { AlbumSource.valueOf(source) }.getOrDefault(AlbumSource.LOCAL_MEDIASTORE),
        isSelected = isSelected,
        lastSyncedAt = lastSyncedAt,
        sourceUrl = sourceUrl
    )

    private fun Album.toEntity() = AlbumEntity(
        id = id,
        name = name,
        coverUri = coverUri,
        photoCount = photoCount,
        source = source.name,
        isSelected = isSelected,
        lastSyncedAt = lastSyncedAt,
        sourceUrl = sourceUrl
    )

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
        isEnabled = isEnabled
    )

    private fun PhotoItem.toEntity() = PhotoEntity(
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
        isEnabled = isEnabled
    )
}
