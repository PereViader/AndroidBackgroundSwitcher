package com.pereviader.backgroundswitcher.data.repository

import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
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
            val photos = mediaStoreScanner.getPhotosInAlbum(albumId)
            val existingPhotos = photoDao.getPhotosListByAlbum(albumId).associateBy { it.id }
            val photosToSave = photos.map { photo ->
                val existing = existingPhotos[photo.id]
                photo.toEntity().copy(
                    cropX = existing?.cropX,
                    cropY = existing?.cropY,
                    lastDisplayedAt = existing?.lastDisplayedAt ?: 0L
                )
            }
            photoDao.insertOrUpdateAll(photosToSave)
        }
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
                    photo.toEntity().copy(
                        cropX = existing?.cropX,
                        cropY = existing?.cropY,
                        lastDisplayedAt = existing?.lastDisplayedAt ?: 0L
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
            photoDao.insertOrUpdateAll(parsed.photos.map { it.toEntity() })
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
                photo.toEntity().copy(
                    cropX = existing?.cropX,
                    cropY = existing?.cropY,
                    lastDisplayedAt = existing?.lastDisplayedAt ?: 0L
                )
            }
            albumDao.insertOrUpdate(updatedEntity)
            photoDao.deletePhotosByAlbum(albumId)
            photoDao.insertOrUpdateAll(photosToSave)
            updatedEntity.toDomain()
        }
    }

    override suspend fun syncAllSelectedAlbums() {
        val selected = albumDao.getSelectedAlbums().first()
        for (album in selected) {
            when (album.source) {
                AlbumSource.LOCAL_MEDIASTORE.name -> {
                    val photos = mediaStoreScanner.getPhotosInAlbum(album.id)
                    val existingPhotos = photoDao.getPhotosListByAlbum(album.id).associateBy { it.id }
                    val photosToSave = photos.map { photo ->
                        val existing = existingPhotos[photo.id]
                        photo.toEntity().copy(
                            cropX = existing?.cropX,
                            cropY = existing?.cropY,
                            lastDisplayedAt = existing?.lastDisplayedAt ?: 0L
                        )
                    }
                    photoDao.insertOrUpdateAll(photosToSave)
                    albumDao.insertOrUpdate(
                        album.copy(
                            photoCount = photos.size,
                            coverUri = photos.firstOrNull()?.uri ?: album.coverUri,
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
    }

    override fun getPhotosInAlbum(albumId: String): Flow<List<PhotoItem>> {
        return photoDao.getPhotosByAlbum(albumId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteAlbum(albumId: String) {
        albumDao.deleteAlbum(albumId)
        photoDao.deletePhotosByAlbum(albumId)
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
            PhotoItem(
                id = "${albumId}_${System.currentTimeMillis()}_$index",
                albumId = albumId,
                uri = uri,
                width = 1920,
                height = 1080,
                dateAdded = System.currentTimeMillis() - (index * 1000L),
                lastDisplayedAt = 0L
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
        cropY = cropY
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
        cropY = cropY
    )
}
