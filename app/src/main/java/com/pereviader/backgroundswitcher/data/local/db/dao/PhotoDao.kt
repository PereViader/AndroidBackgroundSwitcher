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

    @Query("""
        SELECT p.* FROM photos p 
        INNER JOIN albums a ON p.albumId = a.id 
        WHERE a.isSelected = 1 
        ORDER BY p.dateAdded DESC
    """)
    fun getPhotosFromSelectedAlbums(): Flow<List<PhotoEntity>>

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

    @Query("UPDATE photos SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updatePhotoEnabled(id: String, isEnabled: Boolean)

    @Query("UPDATE photos SET isEnabled = :isEnabled WHERE uri = :uri")
    suspend fun updatePhotoEnabledByUri(uri: String, isEnabled: Boolean)

    @Query("""
        SELECT p.* FROM photos p 
        INNER JOIN albums a ON p.albumId = a.id 
        WHERE a.isSelected = 1 AND p.isEnabled = 1
        ORDER BY p.lastDisplayedAt ASC 
        LIMIT 1
    """)
    suspend fun getLeastRecentlyUsedPhoto(): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p 
        INNER JOIN albums a ON p.albumId = a.id 
        WHERE a.isSelected = 1 AND p.isEnabled = 1
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomPhoto(): PhotoEntity?

    @Query("""
        SELECT p.* FROM photos p 
        INNER JOIN albums a ON p.albumId = a.id 
        WHERE a.isSelected = 1 AND p.isEnabled = 1
        ORDER BY p.dateAdded DESC 
        LIMIT 1
    """)
    suspend fun getLatestPhoto(): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos p INNER JOIN albums a ON p.albumId = a.id WHERE a.isSelected = 1 AND p.isEnabled = 1")
    suspend fun getSelectedPhotosCount(): Int

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
