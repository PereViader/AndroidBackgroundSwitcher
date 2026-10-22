package com.pereviader.backgroundswitcher.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreAlbumScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Queries MediaStore to discover all device & synced photo albums.
     */
    suspend fun scanAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val albumMap = mutableMapOf<String, MutableAlbumData>()

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val imageId = cursor.getLong(idCol)
                val bucketId = cursor.getString(bucketIdCol) ?: "unknown"
                val bucketName = cursor.getString(bucketNameCol) ?: "Unnamed Album"
                val dateAdded = cursor.getLong(dateCol) * 1000L

                val imageUri = ContentUris.withAppendedId(collection, imageId).toString()

                val data = albumMap.getOrPut(bucketId) {
                    MutableAlbumData(
                        id = bucketId,
                        name = bucketName,
                        coverUri = imageUri,
                        count = 0,
                        latestDate = dateAdded
                    )
                }
                data.count++
            }
        }

        albumMap.values.map {
            Album(
                id = it.id,
                name = it.name,
                coverUri = it.coverUri,
                photoCount = it.count,
                source = AlbumSource.LOCAL_MEDIASTORE,
                isSelected = false,
                lastSyncedAt = it.latestDate
            )
        }
    }

    /**
     * Queries all photos belonging to a specific bucket ID.
     */
    suspend fun getPhotosInAlbum(bucketId: String): List<PhotoItem> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val photos = mutableListOf<PhotoItem>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val imageId = cursor.getLong(idCol)
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val dateAdded = cursor.getLong(dateCol) * 1000L
                val imageUri = ContentUris.withAppendedId(collection, imageId).toString()

                photos.add(
                    PhotoItem(
                        id = "${bucketId}_$imageId",
                        albumId = bucketId,
                        uri = imageUri,
                        width = width,
                        height = height,
                        dateAdded = dateAdded,
                        lastDisplayedAt = 0L
                    )
                )
            }
        }

        photos
    }

    private data class MutableAlbumData(
        val id: String,
        val name: String,
        val coverUri: String,
        var count: Int,
        val latestDate: Long
    )
}
