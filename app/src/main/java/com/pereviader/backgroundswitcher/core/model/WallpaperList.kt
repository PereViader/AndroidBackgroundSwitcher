package com.pereviader.backgroundswitcher.core.model

data class WallpaperList(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val photoCount: Int = 0,
    val coverUris: List<String> = emptyList(),
    val targetScreen: TargetScreen = TargetScreen.BOTH,
    val rotationOrder: RotationOrder = RotationOrder.LEAST_RECENTLY_USED
) {
    companion object {
        const val DEFAULT_LIST_ID = "default_list"
        const val DEFAULT_LIST_NAME = "My Wallpapers"
    }
}
