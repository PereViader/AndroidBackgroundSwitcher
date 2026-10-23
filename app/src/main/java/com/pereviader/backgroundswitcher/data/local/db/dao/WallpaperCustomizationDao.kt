package com.pereviader.backgroundswitcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity

@Dao
interface WallpaperCustomizationDao {

    @Query("SELECT * FROM wallpaper_customizations WHERE imageUri = :uri LIMIT 1")
    suspend fun getCustomization(uri: String): WallpaperCustomizationEntity?

    @Query("SELECT * FROM wallpaper_customizations")
    suspend fun getAllCustomizations(): List<WallpaperCustomizationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(customization: WallpaperCustomizationEntity)

    @Query("DELETE FROM wallpaper_customizations WHERE imageUri = :uri")
    suspend fun deleteCustomization(uri: String): Int

    @Query("DELETE FROM wallpaper_customizations WHERE imageUri IN (:uris)")
    suspend fun deleteCustomizations(uris: List<String>): Int

    /**
     * Deletes any customizations whose referenced image URI no longer exists in any active photo record.
     */
    @Query("DELETE FROM wallpaper_customizations WHERE imageUri NOT IN (SELECT uri FROM photos)")
    suspend fun deleteOrphanedCustomizations(): Int
}
