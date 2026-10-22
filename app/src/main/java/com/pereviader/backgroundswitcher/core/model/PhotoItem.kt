package com.pereviader.backgroundswitcher.core.model

data class PhotoItem(
    val id: String,
    val albumId: String,
    val uri: String,
    val width: Int = 0,
    val height: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastDisplayedAt: Long = 0L
)
