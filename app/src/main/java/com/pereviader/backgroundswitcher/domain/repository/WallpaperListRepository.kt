package com.pereviader.backgroundswitcher.domain.repository

import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import kotlinx.coroutines.flow.Flow

interface WallpaperListRepository {
    fun observeLists(): Flow<List<WallpaperList>>
    suspend fun getLists(): List<WallpaperList>
    fun observePhotosInList(listId: String): Flow<List<PhotoItem>>
    suspend fun getPhotosInList(listId: String): List<PhotoItem>
    suspend fun ensureDefaultListExists(): WallpaperList
    suspend fun createList(name: String): WallpaperList =
        createList(name, TargetScreen.BOTH, RotationOrder.LEAST_RECENTLY_USED)
    suspend fun createList(
        name: String,
        targetScreen: TargetScreen = TargetScreen.BOTH,
        rotationOrder: RotationOrder = RotationOrder.LEAST_RECENTLY_USED
    ): WallpaperList
    suspend fun updateList(
        listId: String,
        name: String,
        targetScreen: TargetScreen,
        rotationOrder: RotationOrder
    )
    suspend fun renameList(listId: String, newName: String)
    suspend fun deleteList(listId: String): Boolean
    suspend fun assignPhotosToList(listId: String, photoIds: List<String>)
    suspend fun removePhotosFromList(listId: String, photoIds: List<String>)
    suspend fun togglePhotoInList(listId: String, photoId: String, assigned: Boolean)
    suspend fun setPhotoLists(photoId: String, targetListIds: Set<String>)
    suspend fun assignAlbumToList(albumId: String, listId: String)
    suspend fun removeAlbumFromList(albumId: String, listId: String)
    suspend fun setAlbumLists(albumId: String, targetListIds: Set<String>)
}
