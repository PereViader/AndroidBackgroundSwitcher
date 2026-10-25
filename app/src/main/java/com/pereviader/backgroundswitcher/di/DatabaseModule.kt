package com.pereviader.backgroundswitcher.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.data.local.db.AppDatabase
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.ScheduleDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE albums ADD COLUMN sourceUrl TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE photos ADD COLUMN cropX REAL")
            database.execSQL("ALTER TABLE photos ADD COLUMN cropY REAL")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE photos ADD COLUMN zoom REAL")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_uri ON photos(uri)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS wallpaper_customizations (
                    imageUri TEXT NOT NULL PRIMARY KEY,
                    cropX REAL,
                    cropY REAL,
                    zoom REAL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            val now = System.currentTimeMillis()
            database.execSQL(
                """
                INSERT OR REPLACE INTO wallpaper_customizations (imageUri, cropX, cropY, zoom, updatedAt)
                SELECT uri, cropX, cropY, NULL, $now
                FROM photos
                WHERE cropX IS NOT NULL OR cropY IS NOT NULL
                GROUP BY uri
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_uri ON photos(uri)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS wallpaper_customizations (
                    imageUri TEXT NOT NULL PRIMARY KEY,
                    cropX REAL,
                    cropY REAL,
                    zoom REAL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE photos ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE wallpaper_customizations ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS photos_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    albumId TEXT NOT NULL,
                    uri TEXT NOT NULL,
                    width INTEGER NOT NULL,
                    height INTEGER NOT NULL,
                    dateAdded INTEGER NOT NULL,
                    lastDisplayedAt INTEGER NOT NULL,
                    cropX REAL,
                    cropY REAL,
                    zoom REAL,
                    isSelected INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO photos_new (id, albumId, uri, width, height, dateAdded, lastDisplayedAt, cropX, cropY, zoom, isSelected)
                SELECT p.id, p.albumId, p.uri, p.width, p.height, p.dateAdded, p.lastDisplayedAt, p.cropX, p.cropY, p.zoom,
                       CASE WHEN a.isSelected = 1 AND p.isEnabled = 1 THEN 1 ELSE 0 END
                FROM photos p
                LEFT JOIN albums a ON p.albumId = a.id
                """.trimIndent()
            )
            database.execSQL("DROP TABLE photos")
            database.execSQL("ALTER TABLE photos_new RENAME TO photos")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_albumId ON photos(albumId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_lastDisplayedAt ON photos(lastDisplayedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_uri ON photos(uri)")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS wallpaper_customizations_new (
                    imageUri TEXT NOT NULL PRIMARY KEY,
                    cropX REAL,
                    cropY REAL,
                    zoom REAL,
                    isSelected INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO wallpaper_customizations_new (imageUri, cropX, cropY, zoom, isSelected, updatedAt)
                SELECT imageUri, cropX, cropY, zoom, isEnabled, updatedAt
                FROM wallpaper_customizations
                """.trimIndent()
            )
            database.execSQL("DROP TABLE wallpaper_customizations")
            database.execSQL("ALTER TABLE wallpaper_customizations_new RENAME TO wallpaper_customizations")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wallpaper_lists` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `isDefault` INTEGER NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_lists_orderIndex` ON `wallpaper_lists` (`orderIndex`)")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `list_photo_cross_ref` (
                    `listId` TEXT NOT NULL,
                    `photoId` TEXT NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`listId`, `photoId`),
                    FOREIGN KEY(`listId`) REFERENCES `wallpaper_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`photoId`) REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_list_photo_cross_ref_listId` ON `list_photo_cross_ref` (`listId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_list_photo_cross_ref_photoId` ON `list_photo_cross_ref` (`photoId`)")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_rules` (
                    `id` TEXT NOT NULL,
                    `listId` TEXT NOT NULL,
                    `screenScope` TEXT NOT NULL,
                    `priority` INTEGER NOT NULL,
                    `startMinuteOfDay` INTEGER NOT NULL,
                    `endMinuteOfDay` INTEGER NOT NULL,
                    `daysOfWeekMask` INTEGER NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`listId`) REFERENCES `wallpaper_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_rules_screenScope_priority` ON `schedule_rules` (`screenScope`, `priority`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_rules_listId` ON `schedule_rules` (`listId`)")

            val now = System.currentTimeMillis()
            database.execSQL(
                """
                INSERT OR IGNORE INTO `wallpaper_lists` (`id`, `name`, `isDefault`, `orderIndex`, `createdAt`)
                VALUES ('${WallpaperList.DEFAULT_LIST_ID}', '${WallpaperList.DEFAULT_LIST_NAME}', 1, 0, $now)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT OR IGNORE INTO `list_photo_cross_ref` (`listId`, `photoId`, `addedAt`)
                SELECT '${WallpaperList.DEFAULT_LIST_ID}', `id`, `dateAdded`
                FROM `photos`
                WHERE `isSelected` = 1
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `wallpaper_lists` ADD COLUMN `targetScreen` TEXT NOT NULL DEFAULT 'BOTH'")
            database.execSQL("ALTER TABLE `wallpaper_lists` ADD COLUMN `rotationOrder` TEXT NOT NULL DEFAULT 'LEAST_RECENTLY_USED'")
            database.execSQL("ALTER TABLE `schedule_rules` ADD COLUMN `isDefaultRule` INTEGER NOT NULL DEFAULT 0")

            for (scope in listOf("SHARED", "HOME", "LOCK")) {
                val ruleId = "default_rule_${scope.lowercase()}"
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `schedule_rules` (`id`, `listId`, `screenScope`, `priority`, `startMinuteOfDay`, `endMinuteOfDay`, `daysOfWeekMask`, `isEnabled`, `isDefaultRule`)
                    VALUES ('$ruleId', '${WallpaperList.DEFAULT_LIST_ID}', '$scope', 9999, 0, 0, 127, 1, 1)
                    """.trimIndent()
                )
            }
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `schedule_rules` ADD COLUMN `ruleType` TEXT NOT NULL DEFAULT 'TIME'")
            database.execSQL("ALTER TABLE `schedule_rules` ADD COLUMN `latitude` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `schedule_rules` ADD COLUMN `longitude` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `schedule_rules` ADD COLUMN `radiusMeters` REAL DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "background_switcher.db"
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `wallpaper_lists` (`id`, `name`, `isDefault`, `orderIndex`, `createdAt`, `targetScreen`, `rotationOrder`)
                    VALUES ('${WallpaperList.DEFAULT_LIST_ID}', '${WallpaperList.DEFAULT_LIST_NAME}', 1, 0, $now, 'BOTH', 'LEAST_RECENTLY_USED')
                    """.trimIndent()
                )
                for (scope in listOf("SHARED", "HOME", "LOCK")) {
                    val ruleId = "default_rule_${scope.lowercase()}"
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `schedule_rules` (`id`, `listId`, `screenScope`, `priority`, `startMinuteOfDay`, `endMinuteOfDay`, `daysOfWeekMask`, `isEnabled`, `isDefaultRule`)
                        VALUES ('$ruleId', '${WallpaperList.DEFAULT_LIST_ID}', '$scope', 9999, 0, 0, 127, 1, 1)
                        """.trimIndent()
                    )
                }
            }
        })
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideAlbumDao(database: AppDatabase): AlbumDao {
        return database.albumDao()
    }

    @Provides
    fun providePhotoDao(database: AppDatabase): PhotoDao {
        return database.photoDao()
    }

    @Provides
    fun provideWallpaperCustomizationDao(database: AppDatabase): WallpaperCustomizationDao {
        return database.wallpaperCustomizationDao()
    }

    @Provides
    fun provideWallpaperListDao(database: AppDatabase): WallpaperListDao {
        return database.wallpaperListDao()
    }

    @Provides
    fun provideScheduleDao(database: AppDatabase): ScheduleDao {
        return database.scheduleDao()
    }
}
