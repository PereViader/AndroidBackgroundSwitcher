package com.pereviader.backgroundswitcher.data.remote

import android.util.Log
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveFolderParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    companion object {
        private const val TAG = "GoogleDriveParser"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val CONSENT_COOKIE =
            "CONSENT=YES+; SOCS=CAISHAgBEhJnd3NfMjAyMzA4MTUtMF9SQzIaAmVuIAEaBgiAo_CmBg"
        private const val TRAILING_URL_PUNCTUATION = ".,;:!?)\\]}'\"»”›>"

        private val INVISIBLE_FORMAT_CHARS_REGEX =
            Regex("""[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]""")

        // Matches drive.google.com/drive/folders/<ID>, /drive/u/0/folders/<ID>, /drive/mobile/folders/<ID>
        private val DRIVE_FOLDERS_PATH_URL_PATTERN = Pattern.compile(
            """((?:https?://)?(?:www\.)?drive\.google\.com/(?:drive/)?(?:u/\d+/)?(?:mobile/)?(?:u/\d+/)?folders/([a-zA-Z0-9_-]+)(?:[^\s"'<>]*))""",
            Pattern.CASE_INSENSITIVE
        )

        // Matches drive.google.com/open?id=<ID>, /folderview?id=<ID>, /embeddedfolderview?id=<ID>
        private val DRIVE_QUERY_ID_URL_PATTERN = Pattern.compile(
            """((?:https?://)?(?:www\.)?drive\.google\.com/(?:open|folderview|embeddedfolderview)\?(?:[^\s"'<>]*?&)?id=([a-zA-Z0-9_-]+)(?:[^\s"'<>]*))""",
            Pattern.CASE_INSENSITIVE
        )

        private val RESOURCE_KEY_PATTERN = Pattern.compile(
            """[?&]resourcekey=([a-zA-Z0-9_-]+)""",
            Pattern.CASE_INSENSITIVE
        )

        private val OG_TITLE_PATTERN = Pattern.compile(
            """<meta\s+(?:property=["']og:title["']\s+content=["']([^"']+)["']|content=["']([^"']+)["']\s+property=["']og:title["'])""",
            Pattern.CASE_INSENSITIVE
        )

        private val TITLE_TAG_PATTERN = Pattern.compile(
            """<title>([^<]+)</title>""",
            Pattern.CASE_INSENSITIVE
        )

        // Matches flip-entry blocks in embeddedfolderview
        private val FLIP_ENTRY_BLOCK_PATTERN = Pattern.compile(
            """<div[^>]+class=["'][^"']*flip-entry[^"']*["'][^>]*id=["']entry-([a-zA-Z0-9_-]+)["'][^>]*>(.*?)</div>\s*</div>""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )

        private val FLIP_ENTRY_ID_PATTERN = Pattern.compile(
            """id=["']entry-([a-zA-Z0-9_-]+)["']""",
            Pattern.CASE_INSENSITIVE
        )

        // Matches Drive array entries associating file ID with an image MIME type:
        // e.g. ["FILE_ID",["FOLDER_ID"],"photo.jpg","image/jpeg",...] or ["FILE_ID","photo.jpg","image/png"]
        private val DRIVE_ARRAY_IMAGE_ENTRY_PATTERN = Pattern.compile(
            """\[\s*(?:null\s*,\s*)?"([a-zA-Z0-9_-]{3,64})"\s*,\s*(?:\[\s*"[^"]*"\s*\]\s*,\s*)?(?:(?:null|"[^"]*"|\d+|true|false)\s*,\s*){0,6}"image/(?:jpeg|jpg|png|webp|gif|bmp|heic|heif|avif)"""",
            Pattern.CASE_INSENSITIVE
        )

        // Matches JSON object entries with id and image mimeType
        private val JSON_OBJECT_IMAGE_ENTRY_PATTERN = Pattern.compile(
            """\{[^{}]*?"id"\s*:\s*"([a-zA-Z0-9_-]{3,64})"[^{}]*?"mimeType"\s*:\s*"image/(?:jpeg|jpg|png|webp|gif|bmp|heic|heif|avif)"[^{}]*\}""",
            Pattern.CASE_INSENSITIVE
        )

        private val JSON_OBJECT_IMAGE_ENTRY_REVERSED_PATTERN = Pattern.compile(
            """\{[^{}]*?"mimeType"\s*:\s*"image/(?:jpeg|jpg|png|webp|gif|bmp|heic|heif|avif)"[^{}]*?"id"\s*:\s*"([a-zA-Z0-9_-]{3,64})"[^{}]*\}""",
            Pattern.CASE_INSENSITIVE
        )

        // Matches non-image or folder entries in Drive JSON arrays so we can exclude their IDs
        private val DRIVE_ARRAY_NON_IMAGE_ENTRY_PATTERN = Pattern.compile(
            """\[\s*(?:null\s*,\s*)?"([a-zA-Z0-9_-]{3,64})"\s*,\s*(?:\[\s*"[^"]*"\s*\]\s*,\s*)?(?:(?:null|"[^"]*"|\d+|true|false)\s*,\s*){0,6}"(?!image/)(?:application|video|audio|text)/[a-zA-Z0-9._+-]+"""",
            Pattern.CASE_INSENSITIVE
        )

        // Matches direct file links / thumbnails / download URLs
        private val DRIVE_FILE_URL_PATTERN = Pattern.compile(
            """https?://(?:www\.)?drive\.google\.com/file/d/([a-zA-Z0-9_-]{3,64})""",
            Pattern.CASE_INSENSITIVE
        )

        private val DRIVE_THUMBNAIL_OR_UC_PATTERN = Pattern.compile(
            """https?://(?:www\.)?drive\.google\.com/(?:thumbnail|uc)\?(?:[^"'\s<>]*?&)?id=([a-zA-Z0-9_-]{3,64})""",
            Pattern.CASE_INSENSITIVE
        )

        private val GOOGLEUSERCONTENT_D_PATTERN = Pattern.compile(
            """https?://[a-zA-Z0-9_.-]*googleusercontent\.com/d/([a-zA-Z0-9_-]{3,64})""",
            Pattern.CASE_INSENSITIVE
        )

        private val DATA_ID_ELEMENT_PATTERN = Pattern.compile(
            """<[^>]+data-id=["']([a-zA-Z0-9_-]{3,64})["'][^>]*>""",
            Pattern.CASE_INSENSITIVE
        )

        private val NON_IMAGE_EXTENSION_REGEX = Regex(
            """\.(?:pdf|txt|doc|docx|xls|xlsx|ppt|pptx|csv|zip|rar|7z|tar|gz|mp4|mkv|avi|mov|wmv|flv|webm|mp3|wav|ogg|flac|m4a|apk|exe|dmg|iso|json|xml|md|html|css|js)\b""",
            RegexOption.IGNORE_CASE
        )

        private val HEX_ESCAPE_REGEX = Regex("""\\x([0-9a-fA-F]{2})""")
    }

    data class ParsedDriveFolder(
        val album: Album,
        val photos: List<PhotoItem>
    )

    /**
     * Extracts the Google Drive folder ID from a URL or shared text, or returns null if invalid.
     */
    fun extractFolderId(text: String): String? {
        val sanitized = sanitizeInput(text)

        val pathMatcher = DRIVE_FOLDERS_PATH_URL_PATTERN.matcher(sanitized)
        if (pathMatcher.find()) {
            val id = pathMatcher.group(2)?.trimEnd { it in TRAILING_URL_PUNCTUATION }
            if (!id.isNullOrBlank()) return id
        }

        val queryMatcher = DRIVE_QUERY_ID_URL_PATTERN.matcher(sanitized)
        if (queryMatcher.find()) {
            val id = queryMatcher.group(2)?.trimEnd { it in TRAILING_URL_PUNCTUATION }
            if (!id.isNullOrBlank()) return id
        }

        return null
    }

    /**
     * Extracts a Google Drive folder URL from arbitrary shared text or HTML, or returns null if none is found.
     */
    fun extractGoogleDriveFolderUrl(text: String): String? {
        val sanitized = sanitizeInput(text)

        val pathMatcher = DRIVE_FOLDERS_PATH_URL_PATTERN.matcher(sanitized)
        if (pathMatcher.find()) {
            val rawUrl = pathMatcher.group(1) ?: return null
            return normalizeExtractedUrl(rawUrl)
        }

        val queryMatcher = DRIVE_QUERY_ID_URL_PATTERN.matcher(sanitized)
        if (queryMatcher.find()) {
            val rawUrl = queryMatcher.group(1) ?: return null
            return normalizeExtractedUrl(rawUrl)
        }

        return null
    }

    /**
     * Alias for [extractGoogleDriveFolderUrl].
     */
    fun extractFolderUrl(text: String): String? = extractGoogleDriveFolderUrl(text)

    /**
     * Returns true if [url] contains a valid Google Drive folder URL.
     */
    fun isValidGoogleDriveFolderUrl(url: String): Boolean {
        return extractFolderId(url) != null
    }

    fun extractResourceKey(text: String): String? {
        val sanitized = sanitizeInput(text)
        val matcher = RESOURCE_KEY_PATTERN.matcher(sanitized)
        if (matcher.find()) {
            return matcher.group(1)?.trimEnd { it in TRAILING_URL_PUNCTUATION }
        }
        return null
    }

    suspend fun parseFolderUrl(rawUrl: String): Result<ParsedDriveFolder> = withContext(ioDispatcher) {
        try {
            val folderUrl = extractGoogleDriveFolderUrl(rawUrl)
            val folderId = extractFolderId(rawUrl)
            if (folderUrl == null || folderId == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid Google Drive folder URL: '$rawUrl'")
                )
            }

            Log.d(TAG, "Fetching Google Drive folder '$folderId' from: $folderUrl")

            val initialHtml = fetchHtml(folderUrl).getOrElse { error ->
                return@withContext Result.failure(error)
            }

            var title = cleanTitle(extractTitle(initialHtml))
            var fileIds = extractImageFileIds(initialHtml, folderId)

            // If the main folder page didn't expose file entries directly, try embeddedfolderview
            if (fileIds.isEmpty() && !folderUrl.contains("embeddedfolderview", ignoreCase = true)) {
                val resourceKey = extractResourceKey(folderUrl)
                val embeddedUrl = buildString {
                    append("https://drive.google.com/embeddedfolderview?id=$folderId")
                    if (!resourceKey.isNullOrBlank()) {
                        append("&resourcekey=$resourceKey")
                    }
                    append("#list")
                }
                fetchHtml(embeddedUrl).onSuccess { embeddedHtml ->
                    if (embeddedHtml.isNotBlank()) {
                        val embeddedTitle = cleanTitle(extractTitle(embeddedHtml))
                        if (title == "Google Drive Folder" && embeddedTitle != "Google Drive Folder") {
                            title = embeddedTitle
                        }
                        fileIds = extractImageFileIds(embeddedHtml, folderId)
                    }
                }
            }

            Log.d(TAG, "Found ${fileIds.size} image files in Google Drive folder '$title'")

            if (fileIds.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No photos could be found in this Google Drive folder. Please verify that the folder link is shared publicly and contains images.")
                )
            }

            val albumId = "gd_$folderId"
            val coverUrl = buildPhotoUrl(fileIds.first(), width = 800)

            val album = Album(
                id = albumId,
                name = title,
                coverUri = coverUrl,
                photoCount = fileIds.size,
                source = AlbumSource.GOOGLE_DRIVE_FOLDER,
                isSelected = true,
                selectedPhotoCount = fileIds.size,
                lastSyncedAt = System.currentTimeMillis(),
                sourceUrl = folderUrl
            )

            val now = System.currentTimeMillis()
            val photos = fileIds.mapIndexed { index, fileId ->
                PhotoItem(
                    id = "${albumId}_$fileId",
                    albumId = albumId,
                    uri = buildPhotoUrl(fileId, width = 2048),
                    width = 2048,
                    height = 1152,
                    dateAdded = now - (index * 1000L),
                    lastDisplayedAt = 0L,
                    isSelected = true
                )
            }

            Result.success(ParsedDriveFolder(album, photos))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Drive folder", e)
            Result.failure(e)
        }
    }

    suspend fun parseAlbumUrl(rawUrl: String): Result<ParsedDriveFolder> = parseFolderUrl(rawUrl)

    fun buildPhotoUrl(fileId: String, width: Int = 2048): String {
        return "https://lh3.googleusercontent.com/d/$fileId=w$width"
    }

    internal fun extractImageFileIds(html: String, folderId: String): LinkedHashSet<String> {
        val imageIds = LinkedHashSet<String>()
        val excludedIds = HashSet<String>()
        excludedIds.add(folderId)

        val unescaped = unescapeDriveScript(html)

        // First collect any IDs explicitly marked as non-image MIME types in Drive JSON arrays
        val nonImageMatcher = DRIVE_ARRAY_NON_IMAGE_ENTRY_PATTERN.matcher(unescaped)
        while (nonImageMatcher.find()) {
            nonImageMatcher.group(1)?.let { excludedIds.add(it) }
        }

        // 1. Parse embeddedfolderview flip-entry blocks if present
        val flipBlockMatcher = FLIP_ENTRY_BLOCK_PATTERN.matcher(html)
        var foundFlipBlocks = false
        while (flipBlockMatcher.find()) {
            foundFlipBlocks = true
            val entryId = flipBlockMatcher.group(1) ?: continue
            val blockContent = flipBlockMatcher.group(2) ?: ""
            if (isFolderOrNonImageEntry(blockContent)) {
                excludedIds.add(entryId)
            } else if (entryId !in excludedIds) {
                imageIds.add(entryId)
            }
        }

        if (!foundFlipBlocks) {
            val flipIdMatcher = FLIP_ENTRY_ID_PATTERN.matcher(html)
            while (flipIdMatcher.find()) {
                val entryId = flipIdMatcher.group(1) ?: continue
                // Check surrounding context (up to 400 chars after id="entry-...")
                val endIdx = (flipIdMatcher.end() + 400).coerceAtMost(html.length)
                val snippet = html.substring(flipIdMatcher.start(), endIdx)
                if (isFolderOrNonImageEntry(snippet)) {
                    excludedIds.add(entryId)
                } else if (entryId !in excludedIds) {
                    imageIds.add(entryId)
                }
            }
        }

        // 2. Parse Drive JSON array image entries (_DRIVE_ivd / AF_initDataCallback)
        val arrayMatcher = DRIVE_ARRAY_IMAGE_ENTRY_PATTERN.matcher(unescaped)
        while (arrayMatcher.find()) {
            val fileId = arrayMatcher.group(1) ?: continue
            if (fileId !in excludedIds) {
                imageIds.add(fileId)
            }
        }

        // 3. Parse JSON object image entries
        val jsonObjMatcher = JSON_OBJECT_IMAGE_ENTRY_PATTERN.matcher(unescaped)
        while (jsonObjMatcher.find()) {
            val fileId = jsonObjMatcher.group(1) ?: continue
            if (fileId !in excludedIds) {
                imageIds.add(fileId)
            }
        }

        val jsonObjRevMatcher = JSON_OBJECT_IMAGE_ENTRY_REVERSED_PATTERN.matcher(unescaped)
        while (jsonObjRevMatcher.find()) {
            val fileId = jsonObjRevMatcher.group(1) ?: continue
            if (fileId !in excludedIds) {
                imageIds.add(fileId)
            }
        }

        // 4. Parse direct googleusercontent /d/<ID> and drive thumbnail/uc URLs
        val gucMatcher = GOOGLEUSERCONTENT_D_PATTERN.matcher(unescaped)
        while (gucMatcher.find()) {
            val fileId = gucMatcher.group(1) ?: continue
            if (fileId !in excludedIds) {
                imageIds.add(fileId)
            }
        }

        val thumbMatcher = DRIVE_THUMBNAIL_OR_UC_PATTERN.matcher(unescaped)
        while (thumbMatcher.find()) {
            val fileId = thumbMatcher.group(1) ?: continue
            if (fileId !in excludedIds) {
                imageIds.add(fileId)
            }
        }

        // 5. Parse drive.google.com/file/d/<ID> links if not excluded
        val fileLinkMatcher = DRIVE_FILE_URL_PATTERN.matcher(unescaped)
        while (fileLinkMatcher.find()) {
            val fileId = fileLinkMatcher.group(1) ?: continue
            val endIdx = (fileLinkMatcher.end() + 250).coerceAtMost(unescaped.length)
            val snippet = unescaped.substring(fileLinkMatcher.start(), endIdx)
            if (isFolderOrNonImageEntry(snippet)) {
                excludedIds.add(fileId)
            } else if (fileId !in excludedIds) {
                imageIds.add(fileId)
            }
        }

        // 6. Parse data-id="<ID>" attributes on HTML elements
        val dataIdMatcher = DATA_ID_ELEMENT_PATTERN.matcher(html)
        while (dataIdMatcher.find()) {
            val fileId = dataIdMatcher.group(1) ?: continue
            val tag = dataIdMatcher.group(0) ?: ""
            val endIdx = (dataIdMatcher.end() + 300).coerceAtMost(html.length)
            val snippet = html.substring(dataIdMatcher.start(), endIdx)
            if (tag.contains("folder", ignoreCase = true) || isFolderOrNonImageEntry(snippet)) {
                excludedIds.add(fileId)
            } else if (fileId !in excludedIds && fileId != "_gd") {
                imageIds.add(fileId)
            }
        }

        return imageIds
    }

    private fun isFolderOrNonImageEntry(snippet: String): Boolean {
        if (snippet.contains("application/vnd.google-apps.folder", ignoreCase = true) ||
            snippet.contains("/drive/folders/", ignoreCase = true) ||
            snippet.contains("embeddedfolderview?id=", ignoreCase = true) ||
            snippet.contains("icon_11_collection_list", ignoreCase = true) ||
            snippet.contains("type/folder", ignoreCase = true)
        ) {
            return true
        }
        if (NON_IMAGE_EXTENSION_REGEX.containsMatchIn(snippet)) {
            return true
        }
        return false
    }

    private fun fetchHtml(url: String): Result<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", CONSENT_COOKIE)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("Failed to access Google Drive folder (HTTP ${response.code}: ${response.message})")
                )
            }

            val html = response.body?.string() ?: ""
            if (html.isBlank()) {
                return Result.failure(Exception("Received empty response from Google Drive"))
            }
            return Result.success(html)
        }
    }

    internal fun extractTitle(html: String): String {
        val ogMatcher = OG_TITLE_PATTERN.matcher(html)
        if (ogMatcher.find()) {
            val ogTitle = (ogMatcher.group(1) ?: ogMatcher.group(2))?.trim()
            if (!ogTitle.isNullOrBlank()) return ogTitle
        }

        val titleTagMatcher = TITLE_TAG_PATTERN.matcher(html)
        if (titleTagMatcher.find()) {
            val titleTag = titleTagMatcher.group(1)?.trim()
            if (!titleTag.isNullOrBlank()) return titleTag
        }

        return "Google Drive Folder"
    }

    internal fun cleanTitle(raw: String): String {
        var clean = unescapeHtml(raw)
        clean = clean.replace(Regex("""\s*-\s*Google Drive.*$""", RegexOption.IGNORE_CASE), "")
        clean = clean.trim()
        return if (clean.isNotBlank() && !clean.equals("Google Drive", ignoreCase = true)) {
            clean
        } else {
            "Google Drive Folder"
        }
    }

    private fun sanitizeInput(input: String): String {
        return input
            .replace(INVISIBLE_FORMAT_CHARS_REGEX, "")
            .replace('\u00A0', ' ')
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#61;", "=")
            .replace("&#x3d;", "=", ignoreCase = true)
    }

    private fun normalizeExtractedUrl(rawMatch: String): String? {
        var cleaned = rawMatch.trim().trimEnd { it in TRAILING_URL_PUNCTUATION }
        if (cleaned.isBlank()) return null
        if (!cleaned.startsWith("http://", ignoreCase = true) &&
            !cleaned.startsWith("https://", ignoreCase = true)
        ) {
            cleaned = "https://$cleaned"
        }
        return cleaned
    }

    private fun unescapeDriveScript(input: String): String {
        val hexDecoded = HEX_ESCAPE_REGEX.replace(input) { matchResult ->
            val hex = matchResult.groupValues[1]
            hex.toIntOrNull(16)?.toChar()?.toString() ?: matchResult.value
        }
        return hexDecoded
            .replace("\\/", "/")
            .replace("\\u003d", "=", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\u0022", "\"", ignoreCase = true)
            .replace("\\\"", "\"")
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
