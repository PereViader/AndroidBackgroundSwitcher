package com.pereviader.backgroundswitcher.data.remote

import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePhotosShareParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val photoUrlPattern = Pattern.compile(
        """(https://lh3\.googleusercontent\.com/[a-zA-Z0-9_\-]+)"""
    )
    private val titlePattern = Pattern.compile(
        """<meta property="og:title" content="([^"]+)">"""
    )

    data class ParsedSharedAlbum(
        val album: Album,
        val photos: List<PhotoItem>
    )

    suspend fun parseAlbumUrl(url: String): Result<ParsedSharedAlbum> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val html = response.body?.string() ?: ""

            // Extract title
            val titleMatcher = titlePattern.matcher(html)
            val title = if (titleMatcher.find()) {
                titleMatcher.group(1) ?: "Google Photos Album"
            } else {
                "Google Photos Album"
            }

            // Extract photos
            val photoMatcher = photoUrlPattern.matcher(html)
            val uniqueUrls = LinkedHashSet<String>()
            while (photoMatcher.find()) {
                val rawUrl = photoMatcher.group(1)
                // Filter out standard Google Photos UI icons or avatars
                if (rawUrl != null && rawUrl.length > 50) {
                    uniqueUrls.add(rawUrl)
                }
            }

            val albumId = "gp_${url.hashCode()}"
            val coverUrl = uniqueUrls.firstOrNull()

            val album = Album(
                id = albumId,
                name = title,
                coverUri = coverUrl,
                photoCount = uniqueUrls.size,
                source = AlbumSource.GOOGLE_PHOTOS_SHARE,
                isSelected = true,
                lastSyncedAt = System.currentTimeMillis()
            )

            val photos = uniqueUrls.mapIndexed { index, photoUrl ->
                PhotoItem(
                    id = "${albumId}_$index",
                    albumId = albumId,
                    // Append high-res parameters to Google User Content URLs
                    uri = "$photoUrl=w2560-h1440-no",
                    width = 2560,
                    height = 1440,
                    dateAdded = System.currentTimeMillis() - (index * 1000L),
                    lastDisplayedAt = 0L
                )
            }

            Result.success(ParsedSharedAlbum(album, photos))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
