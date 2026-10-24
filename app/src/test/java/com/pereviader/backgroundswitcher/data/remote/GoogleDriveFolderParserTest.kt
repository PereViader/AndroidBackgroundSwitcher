package com.pereviader.backgroundswitcher.data.remote

import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class GoogleDriveFolderParserTest {

    @Test
    fun `extractFolderId and extractFolderUrl handle various Google Drive folder URL formats`() {
        val parser = GoogleDriveFolderParser(OkHttpClient())

        assertThat(parser.extractFolderId("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz"))
            .isEqualTo("1AbCdEfGhIjKlMnOpQrStUvWxYz")
        assertThat(parser.extractFolderUrl("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz"))
            .isEqualTo("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz")

        assertThat(parser.extractFolderId("https://drive.google.com/drive/u/0/folders/1AbC_dEf-123?usp=sharing"))
            .isEqualTo("1AbC_dEf-123")
        assertThat(parser.extractFolderUrl("https://drive.google.com/drive/u/0/folders/1AbC_dEf-123?usp=sharing"))
            .isEqualTo("https://drive.google.com/drive/u/0/folders/1AbC_dEf-123?usp=sharing")

        assertThat(parser.extractFolderId("drive.google.com/drive/folders/Folder_XYZ987"))
            .isEqualTo("Folder_XYZ987")
        assertThat(parser.extractFolderUrl("drive.google.com/drive/folders/Folder_XYZ987"))
            .isEqualTo("https://drive.google.com/drive/folders/Folder_XYZ987")

        assertThat(parser.extractFolderId("https://drive.google.com/open?id=1OpenFolderId_456"))
            .isEqualTo("1OpenFolderId_456")
        assertThat(parser.extractFolderUrl("https://drive.google.com/open?id=1OpenFolderId_456"))
            .isEqualTo("https://drive.google.com/open?id=1OpenFolderId_456")

        assertThat(parser.extractFolderId("https://drive.google.com/embeddedfolderview?id=1EmbeddedFolder_789#list"))
            .isEqualTo("1EmbeddedFolder_789")
        assertThat(parser.extractFolderUrl("https://drive.google.com/embeddedfolderview?id=1EmbeddedFolder_789#list"))
            .isEqualTo("https://drive.google.com/embeddedfolderview?id=1EmbeddedFolder_789#list")

        assertThat(parser.isValidGoogleDriveFolderUrl("https://drive.google.com/drive/folders/1AbCdEf"))
            .isTrue()
        assertThat(parser.isValidGoogleDriveFolderUrl("https://photos.app.goo.gl/XYZ123"))
            .isFalse()
        assertThat(parser.isValidGoogleDriveFolderUrl("https://drive.google.com/file/d/1SingleFileId/view"))
            .isFalse()
        assertThat(parser.extractFolderUrl("No links here"))
            .isNull()
    }

    @Test
    fun `extractFolderUrl strips trailing punctuation, invisible bidi marks, and unescapes HTML entities`() {
        val parser = GoogleDriveFolderParser(OkHttpClient())

        assertThat(
            parser.extractFolderUrl(
                "Check out my wallpapers folder (https://drive.google.com/drive/folders/1AbC_123-def?usp=sharing)."
            )
        ).isEqualTo("https://drive.google.com/drive/folders/1AbC_123-def?usp=sharing")

        val bidiWrapped = "Shared Drive folder:\n\u2068https://drive.google.com/drive/folders/1BidiFolder_999\u2069"
        assertThat(parser.extractFolderUrl(bidiWrapped))
            .isEqualTo("https://drive.google.com/drive/folders/1BidiFolder_999")
        assertThat(parser.extractFolderId(bidiWrapped))
            .isEqualTo("1BidiFolder_999")

        val htmlEncoded = "https://drive.google.com/drive/folders/1ResFolder_111?resourcekey=0-abc&amp;usp=sharing"
        assertThat(parser.extractFolderUrl(htmlEncoded))
            .isEqualTo("https://drive.google.com/drive/folders/1ResFolder_111?resourcekey=0-abc&usp=sharing")
        assertThat(parser.extractResourceKey(htmlEncoded))
            .isEqualTo("0-abc")
    }

    @Test
    fun `parseFolderUrl parses standard Drive folder HTML and filters out non-image files`() = runTest {
        val folderId = "1DriveFolder_Standard"
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Minimalist Wallpapers - Google Drive">
                <script>
                window['_DRIVE_ivd'] = '[[[\x221ImgFile_One\x22,[\x221DriveFolder_Standard\x22],\x22sunset.jpg\x22,\x22image/jpeg\x22],[\x221ImgFile_Two\x22,[\x221DriveFolder_Standard\x22],\x22forest.png\x22,\x22image/png\x22],[\x221DocFile_Ignore\x22,[\x221DriveFolder_Standard\x22],\x22readme.pdf\x22,\x22application/pdf\x22],[\x221SubFolder_Ignore\x22,[\x221DriveFolder_Standard\x22],\x22Archive\x22,\x22application/vnd.google-apps.folder\x22]]]';
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

        val parser = GoogleDriveFolderParser(mockClient)
        val folderUrl = "https://drive.google.com/drive/folders/$folderId?usp=sharing"
        val result = parser.parseFolderUrl(folderUrl)

        assertThat(result.isSuccess).isTrue()
        val parsed = result.getOrThrow()

        assertThat(parsed.album.id).isEqualTo("gd_$folderId")
        assertThat(parsed.album.name).isEqualTo("Minimalist Wallpapers")
        assertThat(parsed.album.source).isEqualTo(AlbumSource.GOOGLE_DRIVE_FOLDER)
        assertThat(parsed.album.sourceUrl).isEqualTo(folderUrl)
        assertThat(parsed.album.coverUri).isEqualTo("https://lh3.googleusercontent.com/d/1ImgFile_One=w800")
        assertThat(parsed.album.photoCount).isEqualTo(2)

        assertThat(parsed.photos).hasSize(2)
        assertThat(parsed.photos[0].id).isEqualTo("gd_${folderId}_1ImgFile_One")
        assertThat(parsed.photos[0].uri).isEqualTo("https://lh3.googleusercontent.com/d/1ImgFile_One=w2048")
        assertThat(parsed.photos[1].id).isEqualTo("gd_${folderId}_1ImgFile_Two")
        assertThat(parsed.photos[1].uri).isEqualTo("https://lh3.googleusercontent.com/d/1ImgFile_Two=w2048")
    }

    @Test
    fun `parseFolderUrl parses embeddedfolderview HTML and falls back when main page has no direct entries`() = runTest {
        val folderId = "1DriveFolder_Embedded"
        val shellHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Cyberpunk &amp; Neon - Google Drive">
            </head>
            <body>Loading Drive...</body>
            </html>
        """.trimIndent()

        val embeddedHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Cyberpunk &amp; Neon</title></head>
            <body>
                <div class="flip-entries">
                    <div class="flip-entry" id="entry-1Neon_PicA">
                        <div class="flip-entry-info">
                            <a href="https://drive.google.com/file/d/1Neon_PicA/view?usp=drive_web">
                                <div class="flip-entry-title">city_night.webp</div>
                            </a>
                        </div>
                    </div>
                    <div class="flip-entry" id="entry-1Neon_PicB">
                        <div class="flip-entry-info">
                            <a href="https://drive.google.com/file/d/1Neon_PicB/view?usp=drive_web">
                                <div class="flip-entry-title">rain_street.jpg</div>
                            </a>
                        </div>
                    </div>
                    <div class="flip-entry" id="entry-1Notes_Doc">
                        <div class="flip-entry-info">
                            <a href="https://drive.google.com/file/d/1Notes_Doc/view?usp=drive_web">
                                <div class="flip-entry-title">license.txt</div>
                            </a>
                        </div>
                    </div>
                    <div class="flip-entry" id="entry-1Child_Folder">
                        <div class="flip-entry-info">
                            <a href="https://drive.google.com/drive/folders/1Child_Folder">
                                <div class="flip-entry-title">Raw</div>
                            </a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val reqUrl = chain.request().url.toString()
                val body = if (reqUrl.contains("embeddedfolderview")) embeddedHtml else shellHtml
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val parser = GoogleDriveFolderParser(mockClient)
        val result = parser.parseFolderUrl("https://drive.google.com/drive/folders/$folderId")

        assertThat(result.isSuccess).isTrue()
        val parsed = result.getOrThrow()

        assertThat(parsed.album.name).isEqualTo("Cyberpunk & Neon")
        assertThat(parsed.photos).hasSize(2)
        assertThat(parsed.photos.map { it.uri }).containsExactly(
            "https://lh3.googleusercontent.com/d/1Neon_PicA=w2048",
            "https://lh3.googleusercontent.com/d/1Neon_PicB=w2048"
        ).inOrder()
    }

    @Test
    fun `parseFolderUrl returns failure when folder contains no photos`() = runTest {
        val emptyHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Empty Folder - Google Drive</title></head>
            <body>No files</body>
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

        val parser = GoogleDriveFolderParser(mockClient)
        val result = parser.parseFolderUrl("https://drive.google.com/drive/folders/1EmptyFolderId")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No photos could be found")
    }

    @Test
    fun `parseFolderUrl returns failure on HTTP error`() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .body("".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val parser = GoogleDriveFolderParser(mockClient)
        val result = parser.parseFolderUrl("https://drive.google.com/drive/folders/1PrivateFolderId")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTP 403")
    }
}
