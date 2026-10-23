package com.pereviader.backgroundswitcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity

@Database(
    entities = [AlbumEntity::class, PhotoEntity::class, WallpaperCustomizationEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao
    abstract fun wallpaperCustomizationDao(): WallpaperCustomizationDao
}
