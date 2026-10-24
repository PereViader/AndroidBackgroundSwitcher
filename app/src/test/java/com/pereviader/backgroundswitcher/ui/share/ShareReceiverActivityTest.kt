package com.pereviader.backgroundswitcher.ui.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTest {

    private lateinit var albumRepository: AlbumRepository
    private lateinit var activity: ShareReceiverActivity

    @Before
    fun setUp() {
        albumRepository = mockk(relaxed = true)

        activity = ShareReceiverActivity().apply {
            this.albumRepository = this@ShareReceiverActivityTest.albumRepository
        }
    }

    @Test
    fun `processIntent imports single image stream via importSharedPhotos`() = runTest {
        val imageUri = Uri.parse("content://media/external/images/media/101")
        val importedAlbum = Album(
            id = "google_photos_picker",
            name = "Shared Photos",
            coverUri = imageUri.toString(),
            photoCount = 1,
            source = AlbumSource.GOOGLE_PHOTOS_PICKER
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
    fun `processIntent imports multiple image streams via importSharedPhotos`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/101")
        val uri2 = Uri.parse("content://media/external/images/media/102")
        val importedAlbum = Album(
            id = "google_photos_picker",
            name = "Shared Photos",
            coverUri = uri1.toString(),
            photoCount = 2,
            source = AlbumSource.GOOGLE_PHOTOS_PICKER
        )
        coEvery {
            albumRepository.importSharedPhotos(listOf(uri1.toString(), uri2.toString()))
        } returns Result.success(importedAlbum)

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri1, uri2))
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) {
            albumRepository.importSharedPhotos(listOf(uri1.toString(), uri2.toString()))
        }
        assertThat(states).contains(ImportState.Success("Shared Photos", 2))
    }

    @Test
    fun `processIntent imports image stream from ClipData when EXTRA_STREAM is missing`() = runTest {
        val imageUri = Uri.parse("content://media/external/images/media/105")
        val importedAlbum = Album(
            id = "google_photos_picker",
            name = "Shared Photos",
            coverUri = imageUri.toString(),
            photoCount = 1,
            source = AlbumSource.GOOGLE_PHOTOS_PICKER
        )
        coEvery { albumRepository.importSharedPhotos(listOf(imageUri.toString())) } returns Result.success(importedAlbum)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            clipData = ClipData.newRawUri("image", imageUri)
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 1) { albumRepository.importSharedPhotos(listOf(imageUri.toString())) }
        assertThat(states).contains(ImportState.Success("Shared Photos", 1))
    }

    @Test
    fun `processIntent emits error when action is unsupported`() = runTest {
        val intent = Intent(Intent.ACTION_MAIN)

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 0) { albumRepository.importSharedPhotos(any(), any()) }
        assertThat(states).containsExactly(ImportState.Error("Unsupported content shared."))
    }

    @Test
    fun `processIntent emits error when no image is received`() = runTest {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 0) { albumRepository.importSharedPhotos(any(), any()) }
        assertThat(states).containsExactly(ImportState.Error("No image received."))
    }

    @Test
    fun `processIntent emits error when unsupported text content is shared`() = runTest {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://photos.app.goo.gl/someAlbum")
        }

        val states = mutableListOf<ImportState>()
        activity.processIntent(intent) { states.add(it) }

        coVerify(exactly = 0) { albumRepository.importSharedPhotos(any(), any()) }
        assertThat(states).containsExactly(ImportState.Error("Unsupported content shared."))
    }
}
