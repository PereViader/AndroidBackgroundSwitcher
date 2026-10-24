package com.pereviader.backgroundswitcher.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var photoDao: PhotoDao
    private lateinit var albumDao: AlbumDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photoDao = db.photoDao()
        albumDao = db.albumDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `getPhotosFromSelectedAlbums only returns photos from selected albums`() = runTest {
        val selectedAlbum = AlbumEntity(id = "album_1", name = "Selected", coverUri = null, photoCount = 1, source = "LOCAL", isSelected = true)
        val unselectedAlbum = AlbumEntity(id = "album_2", name = "Unselected", coverUri = null, photoCount = 1, source = "LOCAL", isSelected = false)
        albumDao.insertOrUpdateAll(listOf(selectedAlbum, unselectedAlbum))

        val photo1 = PhotoEntity(id = "p1", albumId = "album_1", uri = "content://1", dateAdded = 100L)
        val photo2 = PhotoEntity(id = "p2", albumId = "album_2", uri = "content://2", dateAdded = 200L)
        photoDao.insertOrUpdateAll(listOf(photo1, photo2))

        val result = photoDao.getPhotosFromSelectedAlbums().first()
        assertThat(result.map { it.id }).containsExactly("p1")
    }

    @Test
    fun `getLeastRecentlyUsedPhoto returns photo with lowest lastDisplayedAt among selected and enabled photos`() = runTest {
        val album = AlbumEntity(id = "album_1", name = "Selected", coverUri = null, photoCount = 3, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val photoOld = PhotoEntity(id = "p_old", albumId = "album_1", uri = "content://old", lastDisplayedAt = 100L, isEnabled = true)
        val photoRecent = PhotoEntity(id = "p_recent", albumId = "album_1", uri = "content://recent", lastDisplayedAt = 500L, isEnabled = true)
        val photoDisabled = PhotoEntity(id = "p_disabled", albumId = "album_1", uri = "content://disabled", lastDisplayedAt = 10L, isEnabled = false)
        photoDao.insertOrUpdateAll(listOf(photoOld, photoRecent, photoDisabled))

        val lruPhoto = photoDao.getLeastRecentlyUsedPhoto()
        // Should select p_old (100L) rather than p_disabled (10L) because p_disabled isEnabled == false
        assertThat(lruPhoto?.id).isEqualTo("p_old")
    }

    @Test
    fun `getNextChronologicalPhoto returns next photo with greater dateAdded`() = runTest {
        val album = AlbumEntity(id = "album_1", name = "Selected", coverUri = null, photoCount = 3, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val photo1 = PhotoEntity(id = "p1", albumId = "album_1", uri = "content://1", dateAdded = 100L, isEnabled = true)
        val photo2 = PhotoEntity(id = "p2", albumId = "album_1", uri = "content://2", dateAdded = 200L, isEnabled = true)
        val photo3 = PhotoEntity(id = "p3", albumId = "album_1", uri = "content://3", dateAdded = 300L, isEnabled = true)
        photoDao.insertOrUpdateAll(listOf(photo1, photo2, photo3))

        val nextAfter1 = photoDao.getNextChronologicalPhoto(currentDateAdded = 100L)
        assertThat(nextAfter1?.id).isEqualTo("p2")

        val nextAfter2 = photoDao.getNextChronologicalPhoto(currentDateAdded = 200L)
        assertThat(nextAfter2?.id).isEqualTo("p3")

        val nextAfter3 = photoDao.getNextChronologicalPhoto(currentDateAdded = 300L)
        assertThat(nextAfter3).isNull()
    }

    @Test
    fun `updateCustomizationByUri updates crop and zoom for all matching URIs`() = runTest {
        val album = AlbumEntity(id = "album_1", name = "Selected", coverUri = null, photoCount = 2, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val photo1 = PhotoEntity(id = "p1", albumId = "album_1", uri = "content://shared_uri", cropX = 0.5f, cropY = 0.5f, zoom = 1.0f)
        photoDao.insertOrUpdateAll(listOf(photo1))

        photoDao.updateCustomizationByUri(
            uri = "content://shared_uri",
            cropX = 0.25f,
            cropY = 0.75f,
            zoom = 2.2f
        )

        val updated = photoDao.getPhotoById("p1")
        assertThat(updated?.cropX).isEqualTo(0.25f)
        assertThat(updated?.cropY).isEqualTo(0.75f)
        assertThat(updated?.zoom).isEqualTo(2.2f)
    }

    @Test
    fun `updateLastDisplayed updates timestamp correctly`() = runTest {
        val album = AlbumEntity(id = "album_1", name = "Selected", coverUri = null, photoCount = 1, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val photo = PhotoEntity(id = "p1", albumId = "album_1", uri = "content://1", lastDisplayedAt = 0L)
        photoDao.insertOrUpdateAll(listOf(photo))

        photoDao.updateLastDisplayed("p1", 999999L)

        val updated = photoDao.getPhotoById("p1")
        assertThat(updated?.lastDisplayedAt).isEqualTo(999999L)
    }
}
