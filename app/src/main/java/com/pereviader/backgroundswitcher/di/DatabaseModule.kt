package com.pereviader.backgroundswitcher.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pereviader.backgroundswitcher.data.local.db.AppDatabase
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE albums ADD COLUMN sourceUrl TEXT")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE photos ADD COLUMN cropX REAL")
            database.execSQL("ALTER TABLE photos ADD COLUMN cropY REAL")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "background_switcher.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
}
