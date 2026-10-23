package com.pereviader.backgroundswitcher.data.remote

import android.util.Log
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
    companion object {
        private const val TAG = "GooglePhotosParser"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        // Matches quoted googleusercontent URLs (commonly in AF_initDataCallback / scripts)
        private val QUOTED_PHOTO_URL_PATTERN = Pattern.compile(
            """"(https://[^"]*?(?:googleusercontent\.com|usercontent\.google\.com)/[^"=]+?)(?:=[^"]*)?""""
        )

        // Matches raw googleusercontent URLs anywhere in HTML/JS
        private val RAW_PHOTO_URL_PATTERN = Pattern.compile(
            """https://[a-zA-Z0-9_\-\.]*?(?:googleusercontent\.com|usercontent\.google\.com)/(?:pw/|lr/|fife/|p/|d/)?[a-zA-Z0-9_\-]+"""
        )

        private val OG_TITLE_PATTERN = Pattern.compile(
            """<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )

        private val TITLE_TAG_PATTERN = Pattern.compile(
            """<title>([^<]+)</title>""",
            Pattern.CASE_INSENSITIVE
        )

        private val OG_IMAGE_PATTERN = Pattern.compile(
            """<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )

        private val URL_FINDER_PATTERN = Pattern.compile(
            """(https?://[^\s"'<>]+|photos\.(?:app\.goo\.gl|google\.com)/[^\s"'<>]+)"""
        )
    }

    data class ParsedSharedAlbum(
        val album: Album,
        val photos: List<PhotoItem>
    )

    fun extractUrl(text: String): String? {
        val matcher = URL_FINDER_PATTERN.matcher(text)
        if (matcher.find()) {
            var found = matcher.group(1) ?: return null
            if (!found.startsWith("http://") && !found.startsWith("https://")) {
                found = "https://$found"
            }
            return found
        }
        return null
    }

    suspend fun parseAlbumUrl(rawUrl: String): Result<ParsedSharedAlbum> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = extractUrl(rawUrl) ?: rawUrl.trim()
            if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid URL format: '$rawUrl'")
                )
            }

            Log.d(TAG, "Fetching album from: $normalizedUrl")

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to access Google Photos link (HTTP ${response.code}: ${response.message})")
                )
            }

            val html = response.body?.string() ?: ""
            if (html.isBlank()) {
                return@withContext Result.failure(Exception("Received empty response from Google Photos"))
            }

            // 1. Extract and clean Title
            val rawTitle = extractTitle(html)
            val title = cleanTitle(rawTitle)

            // 2. Extract og:image (often the cover or primary photo)
            val ogImageCover = extractOgImage(html)

            // 3. Extract all valid photo URLs
            val uniqueBaseUrls = LinkedHashSet<String>()

            // If og:image is a valid photo URL, add it first
            if (ogImageCover != null && isValidPhotoUrl(ogImageCover)) {
                uniqueBaseUrls.add(cleanBaseUrl(ogImageCover))
            }

            // Match quoted URLs (from script payloads)
            val quotedMatcher = QUOTED_PHOTO_URL_PATTERN.matcher(html)
            while (quotedMatcher.find()) {
                val candidate = quotedMatcher.group(1)
                if (candidate != null && isValidPhotoUrl(candidate)) {
                    uniqueBaseUrls.add(cleanBaseUrl(candidate))
                }
            }

            // Match raw URLs
            val rawMatcher = RAW_PHOTO_URL_PATTERN.matcher(html)
            while (rawMatcher.find()) {
                val candidate = rawMatcher.group(0)
                if (candidate != null && isValidPhotoUrl(candidate)) {
                    uniqueBaseUrls.add(cleanBaseUrl(candidate))
                }
            }

            Log.d(TAG, "Found ${uniqueBaseUrls.size} unique photo URLs in album '$title'")

            if (uniqueBaseUrls.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No photos could be found in this album. Please verify that the album link is shared publicly and contains photos.")
                )
            }

            val albumId = "gp_${normalizedUrl.hashCode()}"
            val coverBaseUrl = uniqueBaseUrls.firstOrNull()
            val coverUrl = coverBaseUrl?.let { "$it=s800" }

            val album = Album(
                id = albumId,
                name = title,
                coverUri = coverUrl,
                photoCount = uniqueBaseUrls.size,
                source = AlbumSource.GOOGLE_PHOTOS_SHARE,
                isSelected = true,
                lastSyncedAt = System.currentTimeMillis(),
                sourceUrl = normalizedUrl
            )

            val photos = uniqueBaseUrls.mapIndexed { index, photoBaseUrl ->
                PhotoItem(
                    id = "${albumId}_$index",
                    albumId = albumId,
                    // Use =s2560 to serve the full high-definition photo matching phone screen bounds
                    uri = "$photoBaseUrl=s2560",
                    width = 2560,
                    height = 1440,
                    dateAdded = System.currentTimeMillis() - (index * 1000L),
                    lastDisplayedAt = 0L
                )
            }

            Result.success(ParsedSharedAlbum(album, photos))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Photos album", e)
            Result.failure(e)
        }
    }

    private fun extractTitle(html: String): String {
        val ogMatcher = OG_TITLE_PATTERN.matcher(html)
        if (ogMatcher.find()) {
            val ogTitle = ogMatcher.group(1)?.trim()
            if (!ogTitle.isNullOrBlank()) return ogTitle
        }

        val titleTagMatcher = TITLE_TAG_PATTERN.matcher(html)
        if (titleTagMatcher.find()) {
            val titleTag = titleTagMatcher.group(1)?.trim()
            if (!titleTag.isNullOrBlank()) return titleTag
        }

        return "Google Photos Album"
    }

    private fun cleanTitle(raw: String): String {
        var clean = unescapeHtml(raw)
        // Strip common Google Photos branding suffixes
        clean = clean.replace(Regex("""\s*-\s*Google Photos.*$""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s*-\s*Google Fotos.*$""", RegexOption.IGNORE_CASE), "")
        clean = clean.trim()
        return if (clean.isNotBlank() && !clean.equals("Google Photos", ignoreCase = true)) {
            clean
        } else {
            "Google Photos Album"
        }
    }

    private fun extractOgImage(html: String): String? {
        val matcher = OG_IMAGE_PATTERN.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }

    private fun cleanBaseUrl(url: String): String {
        return url.substringBefore("=")
    }

    private fun isValidPhotoUrl(url: String): Boolean {
        val base = cleanBaseUrl(url)
        // Exclude standard Google avatars, UI icons, and logos
        if (base.contains("/a-/") ||
            base.contains("/ogw/") ||
            base.contains("/a/") ||
            base.contains("default-profile") ||
            base.contains("googlelogo") ||
            base.contains("branding")
        ) {
            return false
        }
        // Valid photo keys are long identifiers (tokens or pw/lr paths)
        return base.length >= 45
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
    }
}
