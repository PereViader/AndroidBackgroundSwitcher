package com.pereviader.backgroundswitcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY dateAdded DESC")
    fun getPhotosByAlbum(albumId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isSelected = 1 ORDER BY dateAdded DESC")
    fun observeSelectedPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isSelected = 1 ORDER BY dateAdded DESC")
    fun getPhotosFromSelectedAlbums(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE uri = :uri LIMIT 1")
    suspend fun getPhotoByUri(uri: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    fun observePhotoById(id: String): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE uri = :uri LIMIT 1")
    fun observePhotoByUri(uri: String): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE albumId = :albumId")
    suspend fun getPhotosListByAlbum(albumId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Query("UPDATE photos SET cropX = :cropX, cropY = :cropY, zoom = :zoom WHERE id = :id")
    suspend fun updateCustomization(id: String, cropX: Float?, cropY: Float?, zoom: Float?)

    @Query("UPDATE photos SET cropX = :cropX, cropY = :cropY, zoom = :zoom WHERE uri = :uri")
    suspend fun updateCustomizationByUri(uri: String, cropX: Float?, cropY: Float?, zoom: Float?)

    @Query("UPDATE photos SET cropX = :cropX, cropY = :cropY WHERE id = :id")
    suspend fun updateCrop(id: String, cropX: Float?, cropY: Float?)

    @Query("UPDATE photos SET cropX = :cropX, cropY = :cropY WHERE uri = :uri")
    suspend fun updateCropByUri(uri: String, cropX: Float?, cropY: Float?)

    @Query("UPDATE photos SET isSelected = :isSelected WHERE id = :id")
    suspend fun updatePhotoSelection(id: String, isSelected: Boolean)

    @Query("UPDATE photos SET isSelected = :isSelected WHERE uri = :uri")
    suspend fun updatePhotoSelectionByUri(uri: String, isSelected: Boolean)

    @Query("UPDATE photos SET isSelected = :isSelected WHERE albumId = :albumId")
    suspend fun updateAlbumPhotosSelection(albumId: String, isSelected: Boolean)

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1
        ORDER BY lastDisplayedAt ASC 
        LIMIT 1
    """)
    suspend fun getLeastRecentlyUsedPhoto(): PhotoEntity?

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1 AND id != :excludePhotoId
        ORDER BY lastDisplayedAt ASC 
        LIMIT 1
    """)
    suspend fun getLeastRecentlyUsedPhotoExcluding(excludePhotoId: String): PhotoEntity?

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomPhoto(): PhotoEntity?

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1 AND id != :excludePhotoId
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomPhotoExcluding(excludePhotoId: String): PhotoEntity?

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1
        ORDER BY dateAdded DESC 
        LIMIT 1
    """)
    suspend fun getLatestPhoto(): PhotoEntity?

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1 AND dateAdded > :currentDateAdded
        ORDER BY dateAdded ASC 
        LIMIT 1
    """)
    suspend fun getNextChronologicalPhoto(currentDateAdded: Long): PhotoEntity?

    @Query("""
        SELECT * FROM photos 
        WHERE isSelected = 1
        ORDER BY dateAdded ASC 
        LIMIT 1
    """)
    suspend fun getOldestPhoto(): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId
        ORDER BY p.lastDisplayedAt ASC
        LIMIT 1
    """)
    suspend fun getLeastRecentlyUsedPhotoInList(listId: String): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId AND p.id != :excludePhotoId
        ORDER BY p.lastDisplayedAt ASC
        LIMIT 1
    """)
    suspend fun getLeastRecentlyUsedPhotoInListExcluding(listId: String, excludePhotoId: String): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomPhotoInList(listId: String): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId AND p.id != :excludePhotoId
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomPhotoInListExcluding(listId: String, excludePhotoId: String): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId
        ORDER BY p.dateAdded DESC
        LIMIT 1
    """)
    suspend fun getLatestPhotoInList(listId: String): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId AND p.dateAdded > :currentDateAdded
        ORDER BY p.dateAdded ASC
        LIMIT 1
    """)
    suspend fun getNextChronologicalPhotoInList(listId: String, currentDateAdded: Long): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId
        ORDER BY p.dateAdded ASC
        LIMIT 1
    """)
    suspend fun getOldestPhotoInList(listId: String): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE isSelected = 1")
    suspend fun getSelectedPhotosCount(): Int

    @Query("SELECT COUNT(*) FROM photos WHERE albumId = :albumId AND isSelected = 1")
    suspend fun getSelectedPhotosCountByAlbum(albumId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(photos: List<PhotoEntity>)

    @Query("UPDATE photos SET lastDisplayedAt = :timestamp WHERE id = :id")
    suspend fun updateLastDisplayed(id: String, timestamp: Long)

    @Query("DELETE FROM photos WHERE albumId = :albumId")
    suspend fun deletePhotosByAlbum(albumId: String)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoById(id: String): Int

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<String>): Int

    @Query("DELETE FROM photos WHERE uri = :uri")
    suspend fun deletePhotoByUri(uri: String): Int

    @Query("DELETE FROM photos WHERE uri IN (:uris)")
    suspend fun deletePhotosByUris(uris: List<String>): Int
}
