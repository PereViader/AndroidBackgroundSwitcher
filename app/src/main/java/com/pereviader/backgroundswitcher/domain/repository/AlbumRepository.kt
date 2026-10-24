package com.pereviader.backgroundswitcher.domain.repository

import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(): Flow<List<Album>>
    fun getSelectedAlbums(): Flow<List<Album>>
    suspend fun refreshDeviceAlbums()
    suspend fun toggleAlbumSelection(albumId: String, isSelected: Boolean)
    suspend fun importGooglePhotosAlbum(url: String): Result<Album>
    suspend fun syncGooglePhotosAlbum(albumId: String): Result<Album>
    suspend fun addGoogleDriveFolder(shareUrl: String): Result<Album>
    suspend fun importGoogleDriveFolder(url: String): Result<Album> = addGoogleDriveFolder(url)
    suspend fun syncGoogleDriveFolder(albumId: String): Result<Album>
    suspend fun syncAlbum(albumId: String): Result<Album> = syncGooglePhotosAlbum(albumId)
    suspend fun syncCloudAlbums()
    suspend fun syncAllSelectedAlbums()
    fun getPhotosInAlbum(albumId: String): Flow<List<PhotoItem>>
    fun getPhotosFromSelectedAlbums(): Flow<List<PhotoItem>>
    suspend fun deleteAlbum(albumId: String)
    suspend fun importSharedPhotos(uris: List<String>, albumTitle: String? = null): Result<Album>
}
