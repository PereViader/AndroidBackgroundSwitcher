package com.pereviader.backgroundswitcher.data.saf

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentTreeScannerTest {

    private lateinit var context: Context
    private lateinit var scanner: DocumentTreeScanner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scanner = DocumentTreeScanner(context)
    }

    @Test
    fun `isImageFile returns true for image MIME types`() {
        val imageJpeg = mockk<DocumentFile> {
            every { type } returns "image/jpeg"
            every { name } returns "photo.bin"
        }
        val imagePng = mockk<DocumentFile> {
            every { type } returns "image/png"
            every { name } returns "graphic"
        }
        val imageWebp = mockk<DocumentFile> {
            every { type } returns "image/webp"
            every { name } returns "wallpaper"
        }

        assertThat(scanner.isImageFile(imageJpeg)).isTrue()
        assertThat(scanner.isImageFile(imagePng)).isTrue()
        assertThat(scanner.isImageFile(imageWebp)).isTrue()
    }

    @Test
    fun `isImageFile falls back to file extensions when MIME type is null or generic`() {
        val jpgFile = mockk<DocumentFile> {
            every { type } returns null
            every { name } returns "sunset.JPG"
        }
        val pngFile = mockk<DocumentFile> {
            every { type } returns "application/octet-stream"
            every { name } returns "mountains.png"
        }
        val heicFile = mockk<DocumentFile> {
            every { type } returns null
            every { name } returns "nature.heic"
        }
        val nonImageFile = mockk<DocumentFile> {
            every { type } returns "application/pdf"
            every { name } returns "document.pdf"
        }

        assertThat(scanner.isImageFile(jpgFile)).isTrue()
        assertThat(scanner.isImageFile(pngFile)).isTrue()
        assertThat(scanner.isImageFile(heicFile)).isTrue()
        assertThat(scanner.isImageFile(nonImageFile)).isFalse()
    }
}
