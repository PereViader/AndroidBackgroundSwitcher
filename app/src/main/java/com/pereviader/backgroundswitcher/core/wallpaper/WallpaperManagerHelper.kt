package com.pereviader.backgroundswitcher.core.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
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

            try {
                wallpaperManager.suggestDesiredDimensions(bitmap.width, bitmap.height)
            } catch (_: Exception) {
                // Ignore if security or platform restriction prevents hint suggestion
            }

            val cropHint = Rect(0, 0, bitmap.width, bitmap.height)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, cropHint, false, flags)
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

    fun isLiveWallpaperActive(): Boolean {
        return try {
            val info = wallpaperManager.wallpaperInfo
            info != null && info.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun getLiveWallpaperIntent(): Intent {
        return Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, LiveWallpaperService::class.java)
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
