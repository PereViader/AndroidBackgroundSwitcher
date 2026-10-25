package com.pereviader.backgroundswitcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pereviader.backgroundswitcher.data.local.db.entity.ListPhotoCrossRef
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperListDao {

    @Query("SELECT * FROM wallpaper_lists ORDER BY isDefault DESC, orderIndex ASC, createdAt ASC")
    fun observeAllLists(): Flow<List<WallpaperListEntity>>

    @Query("SELECT * FROM wallpaper_lists ORDER BY isDefault DESC, orderIndex ASC, createdAt ASC")
    suspend fun getAllLists(): List<WallpaperListEntity>

    @Query("SELECT * FROM wallpaper_lists WHERE id = :id LIMIT 1")
    suspend fun getListById(id: String): WallpaperListEntity?

    @Query("SELECT * FROM wallpaper_lists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultList(): WallpaperListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: WallpaperListEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListIgnore(list: WallpaperListEntity)

    @Update
    suspend fun updateList(list: WallpaperListEntity)

    @Query("UPDATE wallpaper_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: String, name: String)

    @Query("UPDATE wallpaper_lists SET name = :name, targetScreen = :targetScreen, rotationOrder = :rotationOrder WHERE id = :id")
    suspend fun updateListDetails(id: String, name: String, targetScreen: String, rotationOrder: String)

    @Query("DELETE FROM wallpaper_lists WHERE id = :id AND isDefault = 0")
    suspend fun deleteListById(id: String): Int

    @Query("SELECT * FROM list_photo_cross_ref")
    fun observeAllCrossRefs(): Flow<List<ListPhotoCrossRef>>

    @Query("SELECT * FROM list_photo_cross_ref")
    suspend fun getAllCrossRefs(): List<ListPhotoCrossRef>

    @Query("SELECT * FROM list_photo_cross_ref WHERE listId = :listId")
    suspend fun getCrossRefsForList(listId: String): List<ListPhotoCrossRef>

    @Query("SELECT listId FROM list_photo_cross_ref WHERE photoId = :photoId")
    suspend fun getListIdsForPhoto(photoId: String): List<String>

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId
        ORDER BY ref.addedAt DESC, p.dateAdded DESC
    """)
    fun observePhotosInList(listId: String): Flow<List<PhotoEntity>>

    @Query("""
        SELECT p.* FROM photos p
        INNER JOIN list_photo_cross_ref ref ON p.id = ref.photoId
        WHERE ref.listId = :listId
        ORDER BY ref.addedAt DESC, p.dateAdded DESC
    """)
    suspend fun getPhotosInList(listId: String): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM list_photo_cross_ref WHERE listId = :listId")
    suspend fun getPhotosCountInList(listId: String): Int

    @Query("SELECT DISTINCT listId FROM list_photo_cross_ref")
    suspend fun getNonEmptyListIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: ListPhotoCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<ListPhotoCrossRef>)

    @Query("DELETE FROM list_photo_cross_ref WHERE listId = :listId AND photoId = :photoId")
    suspend fun deleteCrossRef(listId: String, photoId: String)

    @Query("DELETE FROM list_photo_cross_ref WHERE listId = :listId AND photoId IN (:photoIds)")
    suspend fun deleteCrossRefsForPhotos(listId: String, photoIds: List<String>)

    @Query("DELETE FROM list_photo_cross_ref WHERE photoId = :photoId")
    suspend fun deleteAllCrossRefsForPhoto(photoId: String)

    @Query("DELETE FROM list_photo_cross_ref WHERE listId = :listId")
    suspend fun clearListPhotos(listId: String)
}
