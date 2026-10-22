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
    suspend fun syncAllSelectedAlbums()
    suspend fun getPhotosInAlbum(albumId: String): Flow<List<PhotoItem>>
}
