package com.pereviader.backgroundswitcher.core.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wallpaperManager: WallpaperManager by lazy {
        WallpaperManager.getInstance(context)
    }

    /**
     * Applies a centered-cover bitmap to the system home screen, lock screen, or both.
     *
     * @param bitmap The scaled & cropped bitmap matching device screen resolution.
     * @param targetScreen Target screen (HOME, LOCK, or BOTH).
     * @return Result.success if applied successfully, Result.failure on error.
     */
    fun setWallpaper(bitmap: Bitmap, targetScreen: TargetScreen): Result<Unit> {
        return try {
            val flags = when (targetScreen) {
                TargetScreen.HOME -> WallpaperManager.FLAG_SYSTEM
                TargetScreen.LOCK -> WallpaperManager.FLAG_LOCK
                TargetScreen.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, false, flags)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    fun isWallpaperSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            wallpaperManager.isWallpaperSupported
        } else {
            true
        }
    }

    fun isSetWallpaperAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.isSetWallpaperAllowed
        } else {
            true
        }
    }
}
