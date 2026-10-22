package com.pereviader.backgroundswitcher.core.model

enum class AlbumSource {
    LOCAL_MEDIASTORE,
    GOOGLE_PHOTOS_SHARE,
    GOOGLE_PHOTOS_PICKER
}

data class Album(
    val id: String,
    val name: String,
    val coverUri: String?,
    val photoCount: Int,
    val source: AlbumSource,
    val isSelected: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
