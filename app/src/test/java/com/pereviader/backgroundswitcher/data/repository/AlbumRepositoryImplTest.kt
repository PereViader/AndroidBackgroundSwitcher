package com.pereviader.backgroundswitcher.data.repository

import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperCustomizationDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperCustomizationEntity
import com.pereviader.backgroundswitcher.data.mediastore.MediaStoreAlbumScanner
import com.pereviader.backgroundswitcher.data.remote.GoogleDriveFolderParser
import com.pereviader.backgroundswitcher.data.remote.GooglePhotosShareParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AlbumRepositoryImplTest {

    private lateinit var albumDao: AlbumDao
    private lateinit var photoDao: PhotoDao
    private lateinit var wallpaperCustomizationDao: WallpaperCustomizationDao
    private lateinit var mediaStoreScanner: MediaStoreAlbumScanner
    private lateinit var googlePhotosParser: GooglePhotosShareParser
    private lateinit var googleDriveParser: GoogleDriveFolderParser
    private lateinit var repository: AlbumRepositoryImpl

    @Before
    fun setUp() {
        albumDao = mockk(relaxed = true)
        photoDao = mockk(relaxed = true)
        wallpaperCustomizationDao = mockk(relaxed = true)
        mediaStoreScanner = mockk(relaxed = true)
        googlePhotosParser = mockk(relaxed = true)
        googleDriveParser = mockk(relaxed = true)
        every { googleDriveParser.isValidGoogleDriveFolderUrl(any()) } returns false
        coEvery { wallpaperCustomizationDao.getCustomization(any()) } returns null

        repository = AlbumRepositoryImpl(
            albumDao = albumDao,
            photoDao = photoDao,
            wallpaperCustomizationDao = wallpaperCustomizationDao,
            mediaStoreScanner = mediaStoreScanner,
            googlePhotosParser = googlePhotosParser,
            googleDriveParser = googleDriveParser
        )
    }

    @Test
    fun `importGooglePhotosAlbum parses, saves with sourceUrl, and inserts photos`() = runTest {
        val testUrl = "https://photos.app.goo.gl/testAlbum"
        val album = Album(
            id = "gp_123",
            name = "Vacation",
            coverUri = "https://lh3.googleusercontent.com/cover=s800",
            photoCount = 2,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE,
            isSelected = true,
            sourceUrl = testUrl
        )
        val photos = listOf(
            PhotoItem(id = "p1", albumId = "gp_123", uri = "https://lh3.googleusercontent.com/1=s2560", width = 2560, height = 1440, dateAdded = 1L, lastDisplayedAt = 0L),
            PhotoItem(id = "p2", albumId = "gp_123", uri = "https://lh3.googleusercontent.com/2=s2560", width = 2560, height = 1440, dateAdded = 2L, lastDisplayedAt = 0L)
        )

        coEvery { googlePhotosParser.parseAlbumUrl(testUrl) } returns Result.success(
            GooglePhotosShareParser.ParsedSharedAlbum(album, photos)
        )

        val result = repository.importGooglePhotosAlbum(testUrl)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.name).isEqualTo("Vacation")
        assertThat(result.getOrNull()?.sourceUrl).isEqualTo(testUrl)

        coVerify { albumDao.insertOrUpdate(match { it.id == "gp_123" && it.sourceUrl == testUrl }) }
        coVerify { photoDao.insertOrUpdateAll(match { it.size == 2 }) }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `syncGooglePhotosAlbum refreshes photos using saved sourceUrl and restores customizations`() = runTest {
        val albumId = "gp_123"
        val savedUrl = "https://photos.app.goo.gl/myAlbum"
        val existingEntity = AlbumEntity(
            id = albumId,
            name = "Old Name",
            coverUri = null,
            photoCount = 1,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE.name,
            isSelected = true,
            lastSyncedAt = 100L,
            sourceUrl = savedUrl
        )

        coEvery { albumDao.getAlbumById(albumId) } returns existingEntity
        coEvery { wallpaperCustomizationDao.getCustomization("https://lh3.googleusercontent.com/1=s2560") } returns WallpaperCustomizationEntity(
            imageUri = "https://lh3.googleusercontent.com/1=s2560",
            cropX = 0.2f,
            cropY = 0.8f,
            zoom = 1.5f
        )

        val refreshedAlbum = Album(
            id = albumId,
            name = "Refreshed Name",
            coverUri = "https://lh3.googleusercontent.com/newCover=s800",
            photoCount = 3,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE,
            isSelected = true,
            sourceUrl = savedUrl
        )
        val newPhotos = listOf(
            PhotoItem(id = "p1", albumId = albumId, uri = "https://lh3.googleusercontent.com/1=s2560", width = 2560, height = 1440, dateAdded = 1L, lastDisplayedAt = 0L),
            PhotoItem(id = "p2", albumId = albumId, uri = "https://lh3.googleusercontent.com/2=s2560", width = 2560, height = 1440, dateAdded = 2L, lastDisplayedAt = 0L),
            PhotoItem(id = "p3", albumId = albumId, uri = "https://lh3.googleusercontent.com/3=s2560", width = 2560, height = 1440, dateAdded = 3L, lastDisplayedAt = 0L)
        )

        coEvery { googlePhotosParser.parseAlbumUrl(savedUrl) } returns Result.success(
            GooglePhotosShareParser.ParsedSharedAlbum(refreshedAlbum, newPhotos)
        )

        val result = repository.syncGooglePhotosAlbum(albumId)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.name).isEqualTo("Refreshed Name")
        assertThat(result.getOrNull()?.photoCount).isEqualTo(3)

        coVerify { photoDao.deletePhotosByAlbum(albumId) }
        coVerify {
            photoDao.insertOrUpdateAll(match { list ->
                val p1 = list.first { it.id == "p1" }
                p1.cropX == 0.2f && p1.cropY == 0.8f && p1.zoom == 1.5f
            })
        }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `deleteAlbum deletes album, photos, and removes orphaned customizations`() = runTest {
        repository.deleteAlbum("gp_123")

        coVerify { albumDao.deleteAlbum("gp_123") }
        coVerify { photoDao.deletePhotosByAlbum("gp_123") }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `refreshDeviceAlbums deletes photos no longer in MediaStore and cleans up customizations`() = runTest {
        val albumId = "local_1"
        val existingAlbums = listOf(
            AlbumEntity(
                id = albumId,
                name = "Camera",
                coverUri = "uri1",
                photoCount = 2,
                source = AlbumSource.LOCAL_MEDIASTORE.name,
                isSelected = true
            )
        )
        coEvery { albumDao.getAllAlbums() } returns flowOf(existingAlbums)
        coEvery { mediaStoreScanner.scanAlbums() } returns listOf(
            Album(
                id = albumId,
                name = "Camera",
                coverUri = "uri1",
                photoCount = 1,
                source = AlbumSource.LOCAL_MEDIASTORE,
                isSelected = true
            )
        )

        // DB previously had p1 and p2; MediaStore now only has p1 (p2 was deleted from device)
        val existingPhotosInDb = listOf(
            PhotoEntity(id = "p1", albumId = albumId, uri = "content://media/1"),
            PhotoEntity(id = "p2", albumId = albumId, uri = "content://media/2")
        )
        coEvery { photoDao.getPhotosListByAlbum(albumId) } returns existingPhotosInDb
        coEvery { mediaStoreScanner.getPhotosInAlbum(albumId) } returns listOf(
            PhotoItem(id = "p1", albumId = albumId, uri = "content://media/1")
        )

        repository.refreshDeviceAlbums()

        // p2 should be deleted from DB
        coVerify { photoDao.deletePhotosByIds(listOf("p2")) }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `syncGooglePhotosAlbum preserves selection state from customizations`() = runTest {
        val albumId = "gp_123"
        val savedUrl = "https://photos.app.goo.gl/myAlbum"
        val existingEntity = AlbumEntity(
            id = albumId,
            name = "My Album",
            coverUri = null,
            photoCount = 1,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE.name,
            isSelected = true,
            lastSyncedAt = 100L,
            sourceUrl = savedUrl
        )

        coEvery { albumDao.getAlbumById(albumId) } returns existingEntity
        coEvery { wallpaperCustomizationDao.getCustomization("https://lh3.googleusercontent.com/1=s2560") } returns WallpaperCustomizationEntity(
            imageUri = "https://lh3.googleusercontent.com/1=s2560",
            isSelected = false
        )

        val refreshedAlbum = Album(
            id = albumId,
            name = "My Album",
            coverUri = "https://lh3.googleusercontent.com/newCover=s800",
            photoCount = 1,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE,
            isSelected = true,
            sourceUrl = savedUrl
        )
        val newPhotos = listOf(
            PhotoItem(id = "p1", albumId = albumId, uri = "https://lh3.googleusercontent.com/1=s2560", width = 2560, height = 1440, dateAdded = 1L, lastDisplayedAt = 0L)
        )

        coEvery { googlePhotosParser.parseAlbumUrl(savedUrl) } returns Result.success(
            GooglePhotosShareParser.ParsedSharedAlbum(refreshedAlbum, newPhotos)
        )

        val result = repository.syncGooglePhotosAlbum(albumId)

        assertThat(result.isSuccess).isTrue()
        coVerify {
            photoDao.insertOrUpdateAll(match { list ->
                val p1 = list.first { it.id == "p1" }
                !p1.isSelected
            })
        }
    }

    @Test
    fun `toggleAlbumSelection updates album selection and bulk updates all photos in album`() = runTest {
        val albumId = "local_1"
        val albumEntity = AlbumEntity(
            id = albumId,
            name = "Camera",
            coverUri = "uri1",
            photoCount = 2,
            source = AlbumSource.LOCAL_MEDIASTORE.name,
            isSelected = false
        )
        coEvery { albumDao.getAlbumById(albumId) } returns albumEntity
        coEvery { mediaStoreScanner.getPhotosInAlbum(albumId) } returns listOf(
            PhotoItem(id = "p1", albumId = albumId, uri = "content://media/1"),
            PhotoItem(id = "p2", albumId = albumId, uri = "content://media/2")
        )

        repository.toggleAlbumSelection(albumId, true)

        coVerify { albumDao.updateSelection(albumId, true) }
        coVerify { photoDao.updateAlbumPhotosSelection(albumId, true) }
    }

    @Test
    fun `getPhotosFromSelectedAlbums maps entities to domain photo items`() = runTest {
        val photoEntities = listOf(
            PhotoEntity(
                id = "p1",
                albumId = "album_1",
                uri = "content://media/1",
                width = 1080,
                height = 1920,
                isSelected = true
            ),
            PhotoEntity(
                id = "p2",
                albumId = "album_2",
                uri = "content://media/2",
                width = 1080,
                height = 1920,
                isSelected = false
            )
        )
        coEvery { photoDao.getPhotosFromSelectedAlbums() } returns flowOf(photoEntities)

        val result = repository.getPhotosFromSelectedAlbums()

        result.collect { photos ->
            assertThat(photos).hasSize(2)
            assertThat(photos[0].id).isEqualTo("p1")
            assertThat(photos[0].isSelected).isTrue()
            assertThat(photos[1].id).isEqualTo("p2")
            assertThat(photos[1].isSelected).isFalse()
        }
    }

    @Test
    fun `addGoogleDriveFolder parses, saves with GOOGLE_DRIVE_FOLDER source and sourceUrl, and inserts photos`() = runTest {
        val driveUrl = "https://drive.google.com/drive/folders/1DriveWallpapers"
        val album = Album(
            id = "gd_1DriveWallpapers",
            name = "Drive Wallpapers",
            coverUri = "https://lh3.googleusercontent.com/d/f1=w800",
            photoCount = 2,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER,
            isSelected = true,
            sourceUrl = driveUrl
        )
        val photos = listOf(
            PhotoItem(
                id = "gd_1DriveWallpapers_f1",
                albumId = "gd_1DriveWallpapers",
                uri = "https://lh3.googleusercontent.com/d/f1=w2048",
                width = 2048,
                height = 1152
            ),
            PhotoItem(
                id = "gd_1DriveWallpapers_f2",
                albumId = "gd_1DriveWallpapers",
                uri = "https://lh3.googleusercontent.com/d/f2=w2048",
                width = 2048,
                height = 1152
            )
        )

        coEvery { googleDriveParser.parseFolderUrl(driveUrl) } returns Result.success(
            GoogleDriveFolderParser.ParsedDriveFolder(album, photos)
        )
        coEvery { photoDao.getPhotosListByAlbum("gd_1DriveWallpapers") } returns emptyList()

        val result = repository.addGoogleDriveFolder(driveUrl)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.name).isEqualTo("Drive Wallpapers")
        assertThat(result.getOrNull()?.source).isEqualTo(AlbumSource.GOOGLE_DRIVE_FOLDER)
        assertThat(result.getOrNull()?.sourceUrl).isEqualTo(driveUrl)

        coVerify {
            albumDao.insertOrUpdate(
                match {
                    it.id == "gd_1DriveWallpapers" &&
                        it.source == AlbumSource.GOOGLE_DRIVE_FOLDER.name &&
                        it.sourceUrl == driveUrl
                }
            )
        }
        coVerify { photoDao.insertOrUpdateAll(match { it.size == 2 }) }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `syncCloudAlbums syncs GOOGLE_DRIVE_FOLDER and preserves customizations and selection`() = runTest {
        val albumId = "gd_1DriveFolder"
        val savedUrl = "https://drive.google.com/drive/folders/1DriveFolder"
        val existingAlbumEntity = AlbumEntity(
            id = albumId,
            name = "Drive Album",
            coverUri = "https://lh3.googleusercontent.com/d/f1=w800",
            photoCount = 2,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER.name,
            isSelected = true,
            lastSyncedAt = 100L,
            sourceUrl = savedUrl
        )

        coEvery { albumDao.getSelectedAlbums() } returns flowOf(listOf(existingAlbumEntity))
        coEvery { albumDao.getAllAlbums() } returns flowOf(listOf(existingAlbumEntity))
        coEvery { albumDao.getAlbumById(albumId) } returns existingAlbumEntity

        val existingPhotos = listOf(
            PhotoEntity(
                id = "gd_1DriveFolder_f1",
                albumId = albumId,
                uri = "https://lh3.googleusercontent.com/d/f1=w2048",
                isSelected = false,
                lastDisplayedAt = 500L
            ),
            PhotoEntity(
                id = "gd_1DriveFolder_f2_deleted",
                albumId = albumId,
                uri = "https://lh3.googleusercontent.com/d/f2_deleted=w2048",
                isSelected = true
            )
        )
        coEvery { photoDao.getPhotosListByAlbum(albumId) } returns existingPhotos
        coEvery { wallpaperCustomizationDao.getCustomization("https://lh3.googleusercontent.com/d/f1=w2048") } returns WallpaperCustomizationEntity(
            imageUri = "https://lh3.googleusercontent.com/d/f1=w2048",
            cropX = 0.3f,
            cropY = 0.7f,
            zoom = 1.4f,
            isSelected = false
        )

        // Synced folder now has f1 and f3 (f2_deleted was removed, f3 was added)
        val refreshedAlbum = Album(
            id = albumId,
            name = "Drive Album Updated",
            coverUri = "https://lh3.googleusercontent.com/d/f1=w800",
            photoCount = 2,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER,
            isSelected = true,
            sourceUrl = savedUrl
        )
        val syncedPhotos = listOf(
            PhotoItem(id = "gd_1DriveFolder_f1", albumId = albumId, uri = "https://lh3.googleusercontent.com/d/f1=w2048"),
            PhotoItem(id = "gd_1DriveFolder_f3", albumId = albumId, uri = "https://lh3.googleusercontent.com/d/f3=w2048")
        )
        coEvery { googleDriveParser.parseFolderUrl(savedUrl) } returns Result.success(
            GoogleDriveFolderParser.ParsedDriveFolder(refreshedAlbum, syncedPhotos)
        )

        repository.syncCloudAlbums()

        coVerify { photoDao.deletePhotosByAlbum(albumId) }
        coVerify {
            photoDao.insertOrUpdateAll(
                match { list ->
                    val f1 = list.first { it.id == "gd_1DriveFolder_f1" }
                    val f3 = list.first { it.id == "gd_1DriveFolder_f3" }
                    list.size == 2 &&
                        f1.cropX == 0.3f &&
                        f1.cropY == 0.7f &&
                        f1.zoom == 1.4f &&
                        !f1.isSelected &&
                        f1.lastDisplayedAt == 500L &&
                        f3.isSelected
                }
            )
        }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }
}
