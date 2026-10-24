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
    val intervalSeconds: Long = 3600L,
    val targetScreen: TargetScreen = TargetScreen.BOTH,
    val rotationOrder: RotationOrder = RotationOrder.LEAST_RECENTLY_USED,
    val onlyOnWifi: Boolean = true,
    val isEnabled: Boolean = true,
    val cycleOnUnlock: Boolean = false,
    val doubleTapToCycle: Boolean = true,
    val lastRotatedAt: Long = 0L,
    val currentWallpaperUri: String? = null,
    val currentPhotoId: String? = null
) {
    val intervalMillis: Long
        get() = if (intervalSeconds > 0) intervalSeconds * 1000L else intervalMinutes * 60 * 1000L
}
