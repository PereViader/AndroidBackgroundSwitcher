package com.pereviader.backgroundswitcher.data.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DocumentTreeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ScannedTree(
        val folderName: String,
        val photos: List<PhotoItem>
    )

    open suspend fun scanTree(
        treeUri: Uri,
        albumId: String,
        preferredFolderName: String? = null
    ): ScannedTree = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val resolvedName = preferredFolderName?.takeIf { it.isNotBlank() }
            ?: root?.name?.takeIf { it.isNotBlank() }
            ?: "Google Drive"

        val photos = mutableListOf<PhotoItem>()
        if (root != null) {
            scanDirectory(root, albumId, photos, depth = 0)
        }

        ScannedTree(
            folderName = resolvedName,
            photos = photos
        )
    }

    private fun scanDirectory(
        directory: DocumentFile,
        albumId: String,
        photos: MutableList<PhotoItem>,
        depth: Int
    ) {
        if (depth > 5) return
        val files = directory.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, albumId, photos, depth + 1)
            } else if (file.isFile && isImageFile(file)) {
                val uriStr = file.uri.toString()
                val dateAdded = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                val photoId = "${albumId}_${uriStr.hashCode().toUInt()}"
                photos.add(
                    PhotoItem(
                        id = photoId,
                        albumId = albumId,
                        uri = uriStr,
                        width = 1920,
                        height = 1080,
                        dateAdded = dateAdded,
                        lastDisplayedAt = 0L,
                        isSelected = true
                    )
                )
            }
        }
    }

    open fun isImageFile(file: DocumentFile): Boolean {
        val type = file.type
        if (type != null && type.startsWith("image/", ignoreCase = true)) {
            return true
        }
        val name = file.name?.lowercase(Locale.ROOT).orEmpty()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") || name.endsWith(".webp") ||
            name.endsWith(".heic") || name.endsWith(".heif") ||
            name.endsWith(".gif") || name.endsWith(".bmp")
    }
}
