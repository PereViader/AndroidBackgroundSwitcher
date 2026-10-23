package com.pereviader.backgroundswitcher.data.repository

import android.content.Context
import coil.ImageLoader
import com.pereviader.backgroundswitcher.core.wallpaper.WallpaperManagerHelper
import com.pereviader.backgroundswitcher.data.local.datastore.PreferencesManager
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class WallpaperRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var photoDao: PhotoDao
    private lateinit var wallpaperCustomizationDao: WallpaperCustomizationDao
    private lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    private lateinit var imageLoader: ImageLoader
    private lateinit var repository: WallpaperRepositoryImpl

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        photoDao = mockk(relaxed = true)
        wallpaperCustomizationDao = mockk(relaxed = true)
        wallpaperManagerHelper = mockk(relaxed = true)
        imageLoader = mockk(relaxed = true)

        coEvery { preferencesManager.settingsFlow } returns flowOf(mockk(relaxed = true))

        repository = WallpaperRepositoryImpl(
            context = context,
            preferencesManager = preferencesManager,
            photoDao = photoDao,
            wallpaperCustomizationDao = wallpaperCustomizationDao,
            wallpaperManagerHelper = wallpaperManagerHelper,
            imageLoader = imageLoader
        )
    }

    @Test
    fun `updatePhotoCustomization saves customization per image URI in DAO and updates photos table`() = runTest {
        val photo = PhotoEntity(
            id = "photo_1",
            albumId = "album_1",
            uri = "content://media/external/images/media/42"
        )
        coEvery { photoDao.getPhotoById("photo_1") } returns photo

        repository.updatePhotoCustomization(
            photoId = "photo_1",
            cropX = 0.3f,
            cropY = 0.7f,
            zoom = 1.8f
        )

        coVerify {
            wallpaperCustomizationDao.insertOrUpdate(
                match {
                    it.imageUri == "content://media/external/images/media/42" &&
                        it.cropX == 0.3f &&
                        it.cropY == 0.7f &&
                        it.zoom == 1.8f
                }
            )
        }
        coVerify {
            photoDao.updateCustomizationByUri("content://media/external/images/media/42", 0.3f, 0.7f, 1.8f)
        }
    }

    @Test
    fun `updatePhotoCustomization with default values deletes customization from DAO and resets photo`() = runTest {
        val photo = PhotoEntity(
            id = "photo_1",
            albumId = "album_1",
            uri = "content://media/external/images/media/42",
            cropX = 0.3f,
            cropY = 0.7f,
            zoom = 1.8f
        )
        coEvery { photoDao.getPhotoById("photo_1") } returns photo

        repository.updatePhotoCustomization(
            photoId = "photo_1",
            cropX = null,
            cropY = null,
            zoom = null
        )

        coVerify {
            wallpaperCustomizationDao.deleteCustomization("content://media/external/images/media/42")
        }
        coVerify {
            photoDao.updateCustomizationByUri("content://media/external/images/media/42", null, null, null)
        }
    }

    @Test
    fun `deleteUnreferenceablePhoto deletes photo by ID and removes orphaned customizations`() = runTest {
        repository.deleteUnreferenceablePhoto("photo_1", "content://media/external/images/media/42")

        coVerify { photoDao.deletePhotoById("photo_1") }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }
}
