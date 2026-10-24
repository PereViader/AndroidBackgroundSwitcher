package com.pereviader.backgroundswitcher.domain.repository

import android.net.Uri
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(): Flow<List<Album>>
    fun getSelectedAlbums(): Flow<List<Album>>
    suspend fun refreshDeviceAlbums()
    suspend fun toggleAlbumSelection(albumId: String, isSelected: Boolean)
    suspend fun importGoogleDriveFolder(treeUri: Uri, folderName: String? = null): Result<Album>
    suspend fun syncGoogleDriveFolder(albumId: String): Result<Album>
    suspend fun importGooglePhotosFromPicker(uris: List<String>, albumName: String? = null): Result<Album>
    suspend fun syncAlbum(albumId: String): Result<Album> = syncGoogleDriveFolder(albumId)
    suspend fun syncGooglePhotosAlbum(albumId: String): Result<Album> = syncAlbum(albumId)
    suspend fun syncCloudAlbums()
    suspend fun syncAllSelectedAlbums()
    fun getPhotosInAlbum(albumId: String): Flow<List<PhotoItem>>
    fun getPhotosFromSelectedAlbums(): Flow<List<PhotoItem>>
    suspend fun deleteAlbum(albumId: String)
    suspend fun importSharedPhotos(uris: List<String>, albumTitle: String? = null): Result<Album>
}
