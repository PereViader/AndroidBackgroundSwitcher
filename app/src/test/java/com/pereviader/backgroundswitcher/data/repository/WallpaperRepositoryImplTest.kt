package com.pereviader.backgroundswitcher.data.repository

import android.content.Context
import coil.ImageLoader
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.core.wallpaper.WallpaperManagerHelper
import com.pereviader.backgroundswitcher.data.local.datastore.PreferencesManager
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
    fun `updatePhotoCustomization persists positioned entry in DAO and never deletes it`() = runTest {
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
            cropX = 0.5f,
            cropY = 0.5f,
            zoom = 1.0f
        )

        coVerify {
            wallpaperCustomizationDao.insertOrUpdate(
                match {
                    it.imageUri == "content://media/external/images/media/42" &&
                        it.cropX == 0.5f &&
                        it.cropY == 0.5f &&
                        it.zoom == 1.0f
                }
            )
        }
        coVerify {
            photoDao.updateCustomizationByUri("content://media/external/images/media/42", 0.5f, 0.5f, 1.0f)
        }
        coVerify(exactly = 0) {
            wallpaperCustomizationDao.deleteCustomization(any())
        }
    }

    @Test
    fun `togglePhotoSelection with false saves deselection in DAO and updates photos table`() = runTest {
        val photo = PhotoEntity(
            id = "photo_1",
            albumId = "album_1",
            uri = "content://media/external/images/media/42",
            isSelected = true
        )
        coEvery { photoDao.getPhotoById("photo_1") } returns photo
        coEvery { wallpaperCustomizationDao.getCustomization("content://media/external/images/media/42") } returns null

        repository.togglePhotoSelection("photo_1", false)

        coVerify { photoDao.updatePhotoSelection("photo_1", false) }
        coVerify {
            wallpaperCustomizationDao.insertOrUpdate(
                match {
                    it.imageUri == "content://media/external/images/media/42" && !it.isSelected
                }
            )
        }
    }

    @Test
    fun `togglePhotoSelection with true preserves existing customization in DAO and selects photo`() = runTest {
        val photo = PhotoEntity(
            id = "photo_1",
            albumId = "album_1",
            uri = "content://media/external/images/media/42",
            cropX = 0.5f,
            cropY = 0.5f,
            zoom = 1.0f,
            isSelected = false
        )
        coEvery { photoDao.getPhotoById("photo_1") } returns photo
        coEvery { wallpaperCustomizationDao.getCustomization("content://media/external/images/media/42") } returns WallpaperCustomizationEntity(
            imageUri = "content://media/external/images/media/42",
            cropX = 0.5f,
            cropY = 0.5f,
            zoom = 1.0f,
            isSelected = false
        )

        repository.togglePhotoSelection("photo_1", true)

        coVerify { photoDao.updatePhotoSelection("photo_1", true) }
        coVerify {
            wallpaperCustomizationDao.insertOrUpdate(
                match {
                    it.imageUri == "content://media/external/images/media/42" &&
                        it.cropX == 0.5f &&
                        it.cropY == 0.5f &&
                        it.zoom == 1.0f &&
                        it.isSelected
                }
            )
        }
        coVerify(exactly = 0) {
            wallpaperCustomizationDao.deleteCustomization(any())
        }
    }

    @Test
    fun `deleteUnreferenceablePhoto deletes photo by ID and removes orphaned customizations`() = runTest {
        repository.deleteUnreferenceablePhoto("photo_1", "content://media/external/images/media/42")

        coVerify { photoDao.deletePhotoById("photo_1") }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `updateSettings updates all preference values including cycleOnUnlock and doubleTapToCycle`() = runTest {
        val settings = WallpaperSettings(
            intervalMinutes = 30L,
            targetScreen = TargetScreen.HOME,
            rotationOrder = RotationOrder.RANDOM,
            onlyOnWifi = false,
            isEnabled = true,
            cycleOnUnlock = true,
            doubleTapToCycle = false
        )

        repository.updateSettings(settings)

        coVerify { preferencesManager.updateIntervalSeconds(settings.intervalSeconds) }
        coVerify { preferencesManager.updateTargetScreen(TargetScreen.HOME) }
        coVerify { preferencesManager.updateRotationOrder(RotationOrder.RANDOM) }
        coVerify { preferencesManager.updateOnlyOnWifi(false) }
        coVerify { preferencesManager.updateIsEnabled(true) }
        coVerify { preferencesManager.updateCycleOnUnlock(true) }
        coVerify { preferencesManager.updateDoubleTapToCycle(false) }
    }

    @Test
    fun `getNextCandidatePhoto with chronological order picks next chronological photo after current`() = runTest {
        val currentPhoto = PhotoEntity(
            id = "photo_current",
            albumId = "album_1",
            uri = "content://current",
            dateAdded = 1000L
        )
        val nextPhoto = PhotoEntity(
            id = "photo_next",
            albumId = "album_1",
            uri = "content://next",
            dateAdded = 2000L
        )
        val settings = WallpaperSettings(
            rotationOrder = RotationOrder.CHRONOLOGICAL,
            currentPhotoId = "photo_current"
        )
        coEvery { preferencesManager.settingsFlow } returns flowOf(settings)
        coEvery { photoDao.getPhotoById("photo_current") } returns currentPhoto
        coEvery { photoDao.getNextChronologicalPhoto(1000L) } returns nextPhoto

        val result = repository.getNextCandidatePhoto()

        assert(result?.id == "photo_next")
        coVerify { photoDao.getNextChronologicalPhoto(1000L) }
    }

    @Test
    fun `getNextCandidatePhoto with chronological order wraps to oldest photo when at the end`() = runTest {
        val currentPhoto = PhotoEntity(
            id = "photo_latest",
            albumId = "album_1",
            uri = "content://latest",
            dateAdded = 5000L
        )
        val oldestPhoto = PhotoEntity(
            id = "photo_oldest",
            albumId = "album_1",
            uri = "content://oldest",
            dateAdded = 500L
        )
        val settings = WallpaperSettings(
            rotationOrder = RotationOrder.CHRONOLOGICAL,
            currentPhotoId = "photo_latest"
        )
        coEvery { preferencesManager.settingsFlow } returns flowOf(settings)
        coEvery { photoDao.getPhotoById("photo_latest") } returns currentPhoto
        coEvery { photoDao.getNextChronologicalPhoto(5000L) } returns null
        coEvery { photoDao.getOldestPhoto() } returns oldestPhoto

        val result = repository.getNextCandidatePhoto()

        assert(result?.id == "photo_oldest")
        coVerify { photoDao.getOldestPhoto() }
    }

    @Test
    fun `getNextCandidatePhoto with random order excludes current photo when currentPhotoId is set`() = runTest {
        val nextPhoto = PhotoEntity(
            id = "photo_random",
            albumId = "album_1",
            uri = "content://random"
        )
        val settings = WallpaperSettings(
            rotationOrder = RotationOrder.RANDOM,
            currentPhotoId = "photo_current"
        )
        coEvery { preferencesManager.settingsFlow } returns flowOf(settings)
        coEvery { photoDao.getRandomPhotoExcluding("photo_current") } returns nextPhoto

        val result = repository.getNextCandidatePhoto()

        assert(result?.id == "photo_random")
        coVerify { photoDao.getRandomPhotoExcluding("photo_current") }
        coVerify(exactly = 0) { photoDao.getRandomPhoto() }
    }

    @Test
    fun `setPhotoAsWallpaper when live wallpaper is active marks photo displayed without setting static bitmap`() = runTest {
        val photoEntity = PhotoEntity(
            id = "photo_selected",
            albumId = "album_1",
            uri = "content://selected"
        )
        val photoItem = PhotoItem(
            id = "photo_selected",
            albumId = "album_1",
            uri = "content://selected"
        )
        coEvery { photoDao.getPhotoById("photo_selected") } returns photoEntity
        coEvery { wallpaperManagerHelper.isLiveWallpaperActive() } returns true

        val result = repository.setPhotoAsWallpaper(photoItem)

        assert(result.isSuccess)
        coVerifyOrder {
            preferencesManager.updateLastRotated(any(), "content://selected", "photo_selected")
            photoDao.updateLastDisplayed("photo_selected", any())
        }
        coVerify(exactly = 0) { wallpaperManagerHelper.setWallpaper(any(), any()) }
    }

    @Test
    fun `getNextCandidatePhoto with least recently used order excludes current photo when currentPhotoId is set`() = runTest {
        val nextPhoto = PhotoEntity(
            id = "photo_lru",
            albumId = "album_1",
            uri = "content://lru"
        )
        val settings = WallpaperSettings(
            rotationOrder = RotationOrder.LEAST_RECENTLY_USED,
            currentPhotoId = "photo_current"
        )
        coEvery { preferencesManager.settingsFlow } returns flowOf(settings)
        coEvery { photoDao.getLeastRecentlyUsedPhotoExcluding("photo_current") } returns nextPhoto

        val result = repository.getNextCandidatePhoto()

        assert(result?.id == "photo_lru")
        coVerify { photoDao.getLeastRecentlyUsedPhotoExcluding("photo_current") }
        coVerify(exactly = 0) { photoDao.getLeastRecentlyUsedPhoto() }
    }
}
