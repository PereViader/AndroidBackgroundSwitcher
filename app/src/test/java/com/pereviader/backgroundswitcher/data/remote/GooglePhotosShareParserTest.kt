package com.pereviader.backgroundswitcher.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class GooglePhotosShareParserTest {

    @Test
    fun `extractUrl handles various shared link formats`() {
        val parser = GooglePhotosShareParser(OkHttpClient())

        assertThat(parser.extractUrl("https://photos.app.goo.gl/XYZ123"))
            .isEqualTo("https://photos.app.goo.gl/XYZ123")

        assertThat(parser.extractUrl("photos.app.goo.gl/XYZ123"))
            .isEqualTo("https://photos.app.goo.gl/XYZ123")

        assertThat(parser.extractUrl("Check out this album! https://photos.app.goo.gl/ABC_123-def"))
            .isEqualTo("https://photos.app.goo.gl/ABC_123-def")

        assertThat(parser.extractUrl("https://photos.google.com/share/AF1QipM...?key=SECRET123"))
            .isEqualTo("https://photos.google.com/share/AF1QipM...?key=SECRET123")

        assertThat(parser.extractUrl("No links here"))
            .isNull()
    }

    @Test
    fun `parseAlbumUrl extracts title, cover, and high-res photo URLs while filtering avatars`() = runTest {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Nature Wallpapers - Google Photos">
                <meta property="og:image" content="https://lh3.googleusercontent.com/pw/AP1GczNw_cover1234567890abcdefghijklmnopqrstuvwxyz=w1200-h630">
                <script>
                AF_initDataCallback({key: 'ds:1', hash: '1', data: [
                  ["photo1", ["https://lh3.googleusercontent.com/pw/AP1GczNw_photo11234567890abcdefghijklmnopqrstuvwxyz=w1920-h1080"]],
                  ["photo2", ["https://photos.fife.usercontent.google.com/pw/AP1GczNw_photo21234567890abcdefghijklmnopqrstuvwxyz=w1920-h1080"]],
                  ["avatar", ["https://lh3.googleusercontent.com/a-/ALV-UjX1234567890=s64-c"]]
                ]});
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleHtml.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val parser = GooglePhotosShareParser(mockClient)
        val result = parser.parseAlbumUrl("https://photos.app.goo.gl/test")

        assertThat(result.isSuccess).isTrue()
        val parsed = result.getOrThrow()

        // Cleaned title
        assertThat(parsed.album.name).isEqualTo("Nature Wallpapers")

        // Cover URL with high quality parameter
        assertThat(parsed.album.coverUri)
            .isEqualTo("https://lh3.googleusercontent.com/pw/AP1GczNw_cover1234567890abcdefghijklmnopqrstuvwxyz=s800")

        // Photos count & URLs with =s2560
        assertThat(parsed.photos).hasSize(3) // cover, photo1, photo2
        assertThat(parsed.photos[0].uri)
            .isEqualTo("https://lh3.googleusercontent.com/pw/AP1GczNw_cover1234567890abcdefghijklmnopqrstuvwxyz=s2560")
        assertThat(parsed.photos[1].uri)
            .isEqualTo("https://lh3.googleusercontent.com/pw/AP1GczNw_photo11234567890abcdefghijklmnopqrstuvwxyz=s2560")
        assertThat(parsed.photos[2].uri)
            .isEqualTo("https://photos.fife.usercontent.google.com/pw/AP1GczNw_photo21234567890abcdefghijklmnopqrstuvwxyz=s2560")

        // Avatar must not be included
        assertThat(parsed.photos.none { it.uri.contains("ALV-UjX") }).isTrue()
    }

    @Test
    fun `parseAlbumUrl returns failure when page contains no photos`() = runTest {
        val emptyHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Empty Album">
            </head>
            <body>No photos here</body>
            </html>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(emptyHtml.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val parser = GooglePhotosShareParser(mockClient)
        val result = parser.parseAlbumUrl("https://photos.app.goo.gl/empty")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No photos could be found")
    }

    @Test
    fun `parseAlbumUrl returns failure on HTTP error`() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val parser = GooglePhotosShareParser(mockClient)
        val result = parser.parseAlbumUrl("https://photos.app.goo.gl/notfound")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTP 404")
    }
}
