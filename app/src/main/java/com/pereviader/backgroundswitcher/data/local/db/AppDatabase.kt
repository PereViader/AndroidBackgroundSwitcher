package com.pereviader.backgroundswitcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.ScheduleDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperListDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.ListPhotoCrossRef
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.ScheduleRuleEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperListEntity

@Database(
    entities = [
        AlbumEntity::class,
        PhotoEntity::class,
        WallpaperCustomizationEntity::class,
        WallpaperListEntity::class,
        ListPhotoCrossRef::class,
        ScheduleRuleEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao
    abstract fun wallpaperCustomizationDao(): WallpaperCustomizationDao
    abstract fun wallpaperListDao(): WallpaperListDao
    abstract fun scheduleDao(): ScheduleDao
}
