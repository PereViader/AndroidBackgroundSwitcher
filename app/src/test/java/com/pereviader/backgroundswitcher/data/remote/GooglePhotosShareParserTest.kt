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

        assertThat(parser.extractUrl("goo.gl/photos/XYZ123"))
            .isEqualTo("https://goo.gl/photos/XYZ123")

        assertThat(parser.extractUrl("Check out this album! https://photos.app.goo.gl/ABC_123-def"))
            .isEqualTo("https://photos.app.goo.gl/ABC_123-def")

        assertThat(parser.extractUrl("https://photos.google.com/share/AF1QipM...?key=SECRET123"))
            .isEqualTo("https://photos.google.com/share/AF1QipM...?key=SECRET123")

        assertThat(parser.extractUrl("No links here"))
            .isNull()
    }

    @Test
    fun `extractUrl strips trailing punctuation, invisible bidi marks, and unescapes HTML entities`() {
        val parser = GooglePhotosShareParser(OkHttpClient())

        // Trailing punctuation around URL in sentence
        assertThat(parser.extractUrl("Join my shared album (https://photos.app.goo.gl/ABC_123-def)."))
            .isEqualTo("https://photos.app.goo.gl/ABC_123-def")

        assertThat(parser.extractUrl("[https://photos.app.goo.gl/ABC_123-def],"))
            .isEqualTo("https://photos.app.goo.gl/ABC_123-def")

        // Multiline Google Photos share sheet text with Unicode BidiFormatter isolates
        val bidiWrappedText = "New photo added to shared album \"Summer\"\n\u2068https://photos.app.goo.gl/XYZ789\u2069"
        assertThat(parser.extractUrl(bidiWrappedText))
            .isEqualTo("https://photos.app.goo.gl/XYZ789")

        // HTML entities in query parameters
        assertThat(parser.extractUrl("https://photos.google.com/share/AF1QipM?key=SECRET123&amp;hl=en"))
            .isEqualTo("https://photos.google.com/share/AF1QipM?key=SECRET123&hl=en")

        // Prioritizes Google Photos URL even if another URL appears first
        val mixedUrls = "See https://support.google.com/photos and album https://photos.app.goo.gl/REAL_ALBUM!"
        assertThat(parser.extractUrl(mixedUrls))
            .isEqualTo("https://photos.app.goo.gl/REAL_ALBUM")
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
    fun `parseAlbumUrl handles reversed meta tag attributes, escaped script URLs, and interstitial redirect pages`() = runTest {
        val interstitialHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="canonical" href="https://photos.google.com/share/AF1QipInterstitialAlbum?key=MYKEY">
            </head>
            <body>Redirecting...</body>
            </html>
        """.trimIndent()

        val albumHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta content="Mountain Trip &amp; Snow - Google Photos" property="og:title">
                <meta content="https://lh3.googleusercontent.com/pw/AP1GczNw_cover9999999999abcdefghijklmnopqrstuvwxyz=w1200-h630" property="og:image">
                <script>
                AF_initDataCallback({key: 'ds:1', hash: '1', data: [
                  ["photo1", ["https:\/\/lh3.googleusercontent.com\/pw\/AP1GczNw_escaped1234567890abcdefghijklmnopqrstuvwxyz\u003dw1920-h1080"]]
                ]});
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = if (url.contains("photos.google.com/share/")) albumHtml else interstitialHtml
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val parser = GooglePhotosShareParser(mockClient)
        val result = parser.parseAlbumUrl("https://photos.app.goo.gl/shortlink")

        assertThat(result.isSuccess).isTrue()
        val parsed = result.getOrThrow()
        assertThat(parsed.album.name).isEqualTo("Mountain Trip & Snow")
        assertThat(parsed.photos).hasSize(2)
        assertThat(parsed.photos[0].uri)
            .isEqualTo("https://lh3.googleusercontent.com/pw/AP1GczNw_cover9999999999abcdefghijklmnopqrstuvwxyz=s2560")
        assertThat(parsed.photos[1].uri)
            .isEqualTo("https://lh3.googleusercontent.com/pw/AP1GczNw_escaped1234567890abcdefghijklmnopqrstuvwxyz=s2560")
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
