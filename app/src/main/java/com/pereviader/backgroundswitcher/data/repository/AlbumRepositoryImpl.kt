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
                lastSyncedAt = System.currentTimeMillis()
            )
        }
        albumDao.insertOrUpdateAll(entitiesToSave)

        // For selected albums, sync their photos
        val selectedAlbumIds = entitiesToSave.filter { it.isSelected }.map { it.id }
        for (albumId in selectedAlbumIds) {
            val photos = mediaStoreScanner.getPhotosInAlbum(albumId)
            photoDao.insertOrUpdateAll(photos.map { it.toEntity() })
        }
    }

    override suspend fun toggleAlbumSelection(albumId: String, isSelected: Boolean) {
        albumDao.updateSelection(albumId, isSelected)
        if (isSelected) {
            val album = albumDao.getAlbumById(albumId)
            if (album != null && album.source == AlbumSource.LOCAL_MEDIASTORE.name) {
                val photos = mediaStoreScanner.getPhotosInAlbum(albumId)
                photoDao.insertOrUpdateAll(photos.map { it.toEntity() })
            }
        }
    }

    override suspend fun importGooglePhotosAlbum(url: String): Result<Album> {
        val parseResult = googlePhotosParser.parseAlbumUrl(url)
        return parseResult.map { parsed ->
            albumDao.insertOrUpdate(parsed.album.toEntity())
            photoDao.insertOrUpdateAll(parsed.photos.map { it.toEntity() })
            parsed.album
        }
    }

    override suspend fun syncAllSelectedAlbums() {
        val selected = albumDao.getSelectedAlbums().first()
        for (album in selected) {
            when (album.source) {
                AlbumSource.LOCAL_MEDIASTORE.name -> {
                    val photos = mediaStoreScanner.getPhotosInAlbum(album.id)
                    photoDao.insertOrUpdateAll(photos.map { it.toEntity() })
                    albumDao.insertOrUpdate(
                        album.copy(
                            photoCount = photos.size,
                            coverUri = photos.firstOrNull()?.uri ?: album.coverUri,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
                // For Google Photos shared albums, remote resync can re-fetch URLs
                AlbumSource.GOOGLE_PHOTOS_SHARE.name -> {
                    // Re-query if link is stored
                }
            }
        }
    }

    override fun getPhotosInAlbum(albumId: String): Flow<List<PhotoItem>> {
        return photoDao.getPhotosByAlbum(albumId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun AlbumEntity.toDomain() = Album(
        id = id,
        name = name,
        coverUri = coverUri,
        photoCount = photoCount,
        source = runCatching { AlbumSource.valueOf(source) }.getOrDefault(AlbumSource.LOCAL_MEDIASTORE),
        isSelected = isSelected,
        lastSyncedAt = lastSyncedAt
    )

    private fun Album.toEntity() = AlbumEntity(
        id = id,
        name = name,
        coverUri = coverUri,
        photoCount = photoCount,
        source = source.name,
        isSelected = isSelected,
        lastSyncedAt = lastSyncedAt
    )

    private fun PhotoEntity.toDomain() = PhotoItem(
        id = id,
        albumId = albumId,
        uri = uri,
        width = width,
        height = height,
        dateAdded = dateAdded,
        lastDisplayedAt = lastDisplayedAt
    )

    private fun PhotoItem.toEntity() = PhotoEntity(
        id = id,
        albumId = albumId,
        uri = uri,
        width = width,
        height = height,
        dateAdded = dateAdded,
        lastDisplayedAt = lastDisplayedAt
    )
}
