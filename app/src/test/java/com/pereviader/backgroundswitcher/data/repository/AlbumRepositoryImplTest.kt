package com.pereviader.backgroundswitcher.data.repository

import android.net.Uri
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
import com.pereviader.backgroundswitcher.data.saf.DocumentTreeScanner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumRepositoryImplTest {

    private lateinit var albumDao: AlbumDao
    private lateinit var photoDao: PhotoDao
    private lateinit var wallpaperCustomizationDao: WallpaperCustomizationDao
    private lateinit var mediaStoreScanner: MediaStoreAlbumScanner
    private lateinit var documentTreeScanner: DocumentTreeScanner
    private lateinit var repository: AlbumRepositoryImpl

    @Before
    fun setUp() {
        albumDao = mockk(relaxed = true)
        photoDao = mockk(relaxed = true)
        wallpaperCustomizationDao = mockk(relaxed = true)
        mediaStoreScanner = mockk(relaxed = true)
        documentTreeScanner = mockk(relaxed = true)
        coEvery { wallpaperCustomizationDao.getCustomization(any()) } returns null

        repository = AlbumRepositoryImpl(
            albumDao = albumDao,
            photoDao = photoDao,
            wallpaperCustomizationDao = wallpaperCustomizationDao,
            mediaStoreScanner = mediaStoreScanner,
            documentTreeScanner = documentTreeScanner
        )
    }

    @Test
    fun `importGoogleDriveFolder scans tree, saves with GOOGLE_DRIVE_FOLDER source and sourceUrl, and inserts photos`() = runTest {
        val treeUri = Uri.parse("content://com.google.android.apps.docs.storage/tree/folder_123")
        val scannedPhotos = listOf(
            PhotoItem(
                id = "p1",
                albumId = "gdrive_123",
                uri = "content://com.google.android.apps.docs.storage/document/f1",
                width = 1920,
                height = 1080
            ),
            PhotoItem(
                id = "p2",
                albumId = "gdrive_123",
                uri = "content://com.google.android.apps.docs.storage/document/f2",
                width = 1920,
                height = 1080
            )
        )

        coEvery { documentTreeScanner.scanTree(treeUri, any(), "Drive Wallpapers") } returns
            DocumentTreeScanner.ScannedTree(folderName = "Drive Wallpapers", photos = scannedPhotos)
        coEvery { photoDao.getPhotosListByAlbum(any()) } returns emptyList()

        val result = repository.importGoogleDriveFolder(treeUri, "Drive Wallpapers")

        assertThat(result.isSuccess).isTrue()
        val album = result.getOrNull()
        assertThat(album?.name).isEqualTo("Drive Wallpapers")
        assertThat(album?.source).isEqualTo(AlbumSource.GOOGLE_DRIVE_FOLDER)
        assertThat(album?.sourceUrl).isEqualTo(treeUri.toString())
        assertThat(album?.photoCount).isEqualTo(2)

        coVerify {
            albumDao.insertOrUpdate(
                match {
                    it.source == AlbumSource.GOOGLE_DRIVE_FOLDER.name &&
                        it.sourceUrl == treeUri.toString() &&
                        it.name == "Drive Wallpapers"
                }
            )
        }
        coVerify { photoDao.insertOrUpdateAll(match { it.size == 2 }) }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `syncGoogleDriveFolder refreshes photos using saved treeUri and restores customizations`() = runTest {
        val albumId = "gdrive_abc"
        val savedTreeUri = "content://com.google.android.apps.docs.storage/tree/folder_abc"
        val existingEntity = AlbumEntity(
            id = albumId,
            name = "Drive Wallpapers",
            coverUri = null,
            photoCount = 1,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER.name,
            isSelected = true,
            lastSyncedAt = 100L,
            sourceUrl = savedTreeUri
        )

        coEvery { albumDao.getAlbumById(albumId) } returns existingEntity
        coEvery {
            wallpaperCustomizationDao.getCustomization("content://com.google.android.apps.docs.storage/document/f1")
        } returns WallpaperCustomizationEntity(
            imageUri = "content://com.google.android.apps.docs.storage/document/f1",
            cropX = 0.2f,
            cropY = 0.8f,
            zoom = 1.5f
        )

        val refreshedPhotos = listOf(
            PhotoItem(
                id = "${albumId}_1",
                albumId = albumId,
                uri = "content://com.google.android.apps.docs.storage/document/f1"
            ),
            PhotoItem(
                id = "${albumId}_2",
                albumId = albumId,
                uri = "content://com.google.android.apps.docs.storage/document/f2"
            )
        )

        coEvery { documentTreeScanner.scanTree(Uri.parse(savedTreeUri), albumId, "Drive Wallpapers") } returns
            DocumentTreeScanner.ScannedTree(folderName = "Drive Wallpapers", photos = refreshedPhotos)

        val result = repository.syncGoogleDriveFolder(albumId)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.photoCount).isEqualTo(2)

        coVerify { photoDao.deletePhotosByAlbum(albumId) }
        coVerify {
            photoDao.insertOrUpdateAll(
                match { list ->
                    val f1 = list.first { it.uri == "content://com.google.android.apps.docs.storage/document/f1" }
                    f1.cropX == 0.2f && f1.cropY == 0.8f && f1.zoom == 1.5f
                }
            )
        }
        coVerify { wallpaperCustomizationDao.deleteOrphanedCustomizations() }
    }

    @Test
    fun `importGooglePhotosFromPicker saves photos with GOOGLE_PHOTOS_PICKER source`() = runTest {
        val pickerUris = listOf(
            "content://media/picker/0/101",
            "content://media/picker/0/102"
        )
        coEvery { photoDao.getPhotosListByAlbum("google_photos_picker") } returns emptyList()

        val result = repository.importGooglePhotosFromPicker(pickerUris)

        assertThat(result.isSuccess).isTrue()
        val album = result.getOrNull()
        assertThat(album?.name).isEqualTo("Google Photos")
        assertThat(album?.source).isEqualTo(AlbumSource.GOOGLE_PHOTOS_PICKER)
        assertThat(album?.photoCount).isEqualTo(2)

        coVerify {
            albumDao.insertOrUpdate(
                match {
                    it.id == "google_photos_picker" &&
                        it.source == AlbumSource.GOOGLE_PHOTOS_PICKER.name
                }
            )
        }
        coVerify { photoDao.insertOrUpdateAll(match { it.size == 2 }) }
    }

    @Test
    fun `deleteAlbum deletes album, photos, and removes orphaned customizations`() = runTest {
        repository.deleteAlbum("album_123")

        coVerify { albumDao.deleteAlbum("album_123") }
        coVerify { photoDao.deletePhotosByAlbum("album_123") }
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
    fun `syncCloudAlbums syncs GOOGLE_DRIVE_FOLDER and preserves customizations and selection`() = runTest {
        val albumId = "gdrive_folder"
        val savedTreeUri = "content://com.google.android.apps.docs.storage/tree/folder_xyz"
        val existingAlbumEntity = AlbumEntity(
            id = albumId,
            name = "Drive Album",
            coverUri = "content://com.google.android.apps.docs.storage/document/f1",
            photoCount = 2,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER.name,
            isSelected = true,
            lastSyncedAt = 100L,
            sourceUrl = savedTreeUri
        )

        coEvery { albumDao.getSelectedAlbums() } returns flowOf(listOf(existingAlbumEntity))
        coEvery { albumDao.getAllAlbums() } returns flowOf(listOf(existingAlbumEntity))
        coEvery { albumDao.getAlbumById(albumId) } returns existingAlbumEntity

        val existingPhotos = listOf(
            PhotoEntity(
                id = "${albumId}_f1",
                albumId = albumId,
                uri = "content://com.google.android.apps.docs.storage/document/f1",
                isSelected = false,
                lastDisplayedAt = 500L
            ),
            PhotoEntity(
                id = "${albumId}_f2_deleted",
                albumId = albumId,
                uri = "content://com.google.android.apps.docs.storage/document/f2_deleted",
                isSelected = true
            )
        )
        coEvery { photoDao.getPhotosListByAlbum(albumId) } returns existingPhotos
        coEvery {
            wallpaperCustomizationDao.getCustomization("content://com.google.android.apps.docs.storage/document/f1")
        } returns WallpaperCustomizationEntity(
            imageUri = "content://com.google.android.apps.docs.storage/document/f1",
            cropX = 0.3f,
            cropY = 0.7f,
            zoom = 1.4f,
            isSelected = false
        )

        val syncedPhotos = listOf(
            PhotoItem(id = "${albumId}_f1", albumId = albumId, uri = "content://com.google.android.apps.docs.storage/document/f1"),
            PhotoItem(id = "${albumId}_f3", albumId = albumId, uri = "content://com.google.android.apps.docs.storage/document/f3")
        )
        coEvery { documentTreeScanner.scanTree(Uri.parse(savedTreeUri), albumId, "Drive Album") } returns
            DocumentTreeScanner.ScannedTree(folderName = "Drive Album Updated", photos = syncedPhotos)

        repository.syncCloudAlbums()

        coVerify { photoDao.deletePhotosByAlbum(albumId) }
        coVerify {
            photoDao.insertOrUpdateAll(
                match { list ->
                    val f1 = list.first { it.uri == "content://com.google.android.apps.docs.storage/document/f1" }
                    val f3 = list.first { it.uri == "content://com.google.android.apps.docs.storage/document/f3" }
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
