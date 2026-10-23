package com.pereviader.backgroundswitcher.data.repository

import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.mediastore.MediaStoreAlbumScanner
import com.pereviader.backgroundswitcher.data.remote.GooglePhotosShareParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AlbumRepositoryImplTest {

    private lateinit var albumDao: AlbumDao
    private lateinit var photoDao: PhotoDao
    private lateinit var mediaStoreScanner: MediaStoreAlbumScanner
    private lateinit var googlePhotosParser: GooglePhotosShareParser
    private lateinit var repository: AlbumRepositoryImpl

    @Before
    fun setUp() {
        albumDao = mockk(relaxed = true)
        photoDao = mockk(relaxed = true)
        mediaStoreScanner = mockk(relaxed = true)
        googlePhotosParser = mockk(relaxed = true)

        repository = AlbumRepositoryImpl(
            albumDao = albumDao,
            photoDao = photoDao,
            mediaStoreScanner = mediaStoreScanner,
            googlePhotosParser = googlePhotosParser
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
    }

    @Test
    fun `syncGooglePhotosAlbum refreshes photos using saved sourceUrl`() = runTest {
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
        coVerify { photoDao.insertOrUpdateAll(match { it.size == 3 }) }
    }

    @Test
    fun `deleteAlbum deletes both album and photos`() = runTest {
        repository.deleteAlbum("gp_123")

        coVerify { albumDao.deleteAlbum("gp_123") }
        coVerify { photoDao.deletePhotosByAlbum("gp_123") }
    }
}
