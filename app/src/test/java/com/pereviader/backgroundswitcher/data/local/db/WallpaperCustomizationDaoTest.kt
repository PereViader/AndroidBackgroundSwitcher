package com.pereviader.backgroundswitcher.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WallpaperCustomizationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var customizationDao: WallpaperCustomizationDao
    private lateinit var photoDao: PhotoDao
    private lateinit var albumDao: AlbumDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        customizationDao = db.wallpaperCustomizationDao()
        photoDao = db.photoDao()
        albumDao = db.albumDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `insertOrUpdate inserts and replaces customization by imageUri`() = runTest {
        val entry1 = WallpaperCustomizationEntity(
            imageUri = "content://media/1",
            cropX = 0.2f,
            cropY = 0.3f,
            zoom = 1.0f
        )
        customizationDao.insertOrUpdate(entry1)

        val retrieved = customizationDao.getCustomization("content://media/1")
        assertThat(retrieved?.cropX).isEqualTo(0.2f)
        assertThat(retrieved?.cropY).isEqualTo(0.3f)

        val updatedEntry = entry1.copy(cropX = 0.8f, zoom = 2.0f)
        customizationDao.insertOrUpdate(updatedEntry)

        val retrievedUpdated = customizationDao.getCustomization("content://media/1")
        assertThat(retrievedUpdated?.cropX).isEqualTo(0.8f)
        assertThat(retrievedUpdated?.zoom).isEqualTo(2.0f)
    }

    @Test
    fun `deleteOrphanedCustomizations deletes only entries whose URI does not exist in photos table`() = runTest {
        val album = AlbumEntity(id = "album_1", name = "Test Album", coverUri = null, photoCount = 1, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val activePhoto = PhotoEntity(id = "p1", albumId = "album_1", uri = "content://media/active")
        photoDao.insertOrUpdateAll(listOf(activePhoto))

        // Insert customization for active photo
        customizationDao.insertOrUpdate(
            WallpaperCustomizationEntity(imageUri = "content://media/active", cropX = 0.1f, cropY = 0.1f, zoom = 1.0f)
        )
        // Insert customization for deleted/orphaned photo
        customizationDao.insertOrUpdate(
            WallpaperCustomizationEntity(imageUri = "content://media/orphaned", cropX = 0.5f, cropY = 0.5f, zoom = 1.5f)
        )

        val deletedCount = customizationDao.deleteOrphanedCustomizations()
        assertThat(deletedCount).isEqualTo(1)

        assertThat(customizationDao.getCustomization("content://media/active")).isNotNull()
        assertThat(customizationDao.getCustomization("content://media/orphaned")).isNull()
    }
}
