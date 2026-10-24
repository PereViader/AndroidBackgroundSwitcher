package com.pereviader.backgroundswitcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE isSelected = 1 OR id IN (SELECT DISTINCT albumId FROM photos WHERE isSelected = 1)")
    fun getSelectedAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(albums: List<AlbumEntity>)

    @Query("UPDATE albums SET isSelected = :isSelected WHERE id = :id")
    suspend fun updateSelection(id: String, isSelected: Boolean)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteAlbum(id: String)
}
