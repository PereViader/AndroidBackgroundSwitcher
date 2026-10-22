package com.pereviader.backgroundswitcher.core.model

enum class TargetScreen {
    HOME,
    LOCK,
    BOTH
}

enum class RotationOrder {
    RANDOM,
    CHRONOLOGICAL,
    LEAST_RECENTLY_USED
}

data class WallpaperSettings(
    val intervalMinutes: Long = 60L,
    val targetScreen: TargetScreen = TargetScreen.BOTH,
    val rotationOrder: RotationOrder = RotationOrder.LEAST_RECENTLY_USED,
    val onlyOnWifi: Boolean = true,
    val isEnabled: Boolean = true,
    val lastRotatedAt: Long = 0L,
    val currentWallpaperUri: String? = null
)
