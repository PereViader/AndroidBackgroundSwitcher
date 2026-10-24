package com.pereviader.backgroundswitcher.ui.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.data.remote.GooglePhotosShareParser
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTest {

    private lateinit var albumRepository: AlbumRepository
    private lateinit var googlePhotosParser: GooglePhotosShareParser
    private lateinit var activity: ShareReceiverActivity

    @Before
    fun setUp() {
        albumRepository = mockk(relaxed = true)
        googlePhotosParser = GooglePhotosShareParser(OkHttpClient())

        activity = ShareReceiverActivity().apply {
            this.albumRepository = this@ShareReceiverActivityTest.albumRepository
            this.googlePhotosParser = this@ShareReceiverActivityTest.googlePhotosParser
        }
    }

    @Test
    fun `processIntent extracts Google Photos link when EXTRA_TEXT is a CharSequence SpannableString`() = runTest {
        val expectedUrl = "https://photos.app.goo.gl/SpannableAlbum123"
        val importedAlbum = Album(
            id = "gp_1",
            name = "Shared Vacation",
            coverUri = null,
            photoCount = 12,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE
        )
        coEvery { albumRepository.importGooglePhotosAlbum(expectedUrl) } returns Result.success(importedAlbum)

        val extras = Bundle().apply {
            putCharSequence(
                Intent.EXTRA_TEXT,
                SpannableString("Join my shared album on Google Photos:\n$expectedUrl")
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtras(extras)
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) { albumRepository.importGooglePhotosAlbum(expectedUrl) }
        assertThat(states).contains(ImportState.Success("Shared Vacation", 12))
    }

    @Test
    fun `processIntent extracts Google Photos link from ClipData when EXTRA_TEXT is missing`() = runTest {
        val expectedUrl = "https://photos.app.goo.gl/ClipDataAlbum456"
        val importedAlbum = Album(
            id = "gp_2",
            name = "ClipData Album",
            coverUri = null,
            photoCount = 8,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE
        )
        coEvery { albumRepository.importGooglePhotosAlbum(expectedUrl) } returns Result.success(importedAlbum)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            clipData = ClipData.newPlainText("Google Photos", "Shared album link: $expectedUrl.")
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) { albumRepository.importGooglePhotosAlbum(expectedUrl) }
        assertThat(states).contains(ImportState.Success("ClipData Album", 8))
    }

    @Test
    fun `processIntent prioritizes Google Photos album URL even when MIME type is image or wildcard with preview stream`() = runTest {
        val expectedUrl = "https://photos.app.goo.gl/RichPreviewAlbum789"
        val importedAlbum = Album(
            id = "gp_3",
            name = "Rich Preview Album",
            coverUri = null,
            photoCount = 25,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE
        )
        coEvery { albumRepository.importGooglePhotosAlbum(expectedUrl) } returns Result.success(importedAlbum)

        val previewThumbnailUri = Uri.parse("content://com.google.android.apps.photos.contentprovider/preview/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, previewThumbnailUri)
            putExtra(Intent.EXTRA_TEXT, "New photo added to shared album\n$expectedUrl")
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) { albumRepository.importGooglePhotosAlbum(expectedUrl) }
        coVerify(exactly = 0) { albumRepository.importSharedPhotos(any(), any()) }
        assertThat(states).contains(ImportState.Success("Rich Preview Album", 25))
    }

    @Test
    fun `processIntent imports shared photos when image URI is shared without album URL`() = runTest {
        val imageUri = Uri.parse("content://media/external/images/media/101")
        val importedAlbum = Album(
            id = "shared_direct",
            name = "Shared Photos",
            coverUri = null,
            photoCount = 1,
            source = AlbumSource.GOOGLE_PHOTOS_SHARE
        )
        coEvery { albumRepository.importSharedPhotos(listOf(imageUri.toString())) } returns Result.success(importedAlbum)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) { albumRepository.importSharedPhotos(listOf(imageUri.toString())) }
        assertThat(states).contains(ImportState.Success("Shared Photos", 1))
    }

    @Test
    fun `processIntent emits error when shared text contains no valid link`() = runTest {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Just some plain text without any URL")
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 0) { albumRepository.importGooglePhotosAlbum(any()) }
        assertThat(states).containsExactly(
            ImportState.Error("No valid Google Photos link was found in the shared text.")
        )
    }

    @Test
    fun `processIntent extracts Google Drive folder link and imports via addGoogleDriveFolder`() = runTest {
        val expectedDriveUrl = "https://drive.google.com/drive/folders/1SharedDriveFolder_ABC?usp=sharing"
        val importedAlbum = Album(
            id = "gd_1SharedDriveFolder_ABC",
            name = "Drive Wallpapers",
            coverUri = null,
            photoCount = 15,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER,
            sourceUrl = expectedDriveUrl
        )
        coEvery { albumRepository.addGoogleDriveFolder(expectedDriveUrl) } returns Result.success(importedAlbum)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Here is my Google Drive wallpaper folder:\n$expectedDriveUrl")
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) { albumRepository.addGoogleDriveFolder(expectedDriveUrl) }
        coVerify(exactly = 0) { albumRepository.importGooglePhotosAlbum(any()) }
        assertThat(states).contains(ImportState.Success("Drive Wallpapers", 15))
    }
}
