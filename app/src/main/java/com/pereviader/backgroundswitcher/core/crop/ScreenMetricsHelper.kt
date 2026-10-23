package com.pereviader.backgroundswitcher.core.crop

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.WindowManager
import androidx.window.layout.WindowMetricsCalculator

data class ScreenDimensions(val width: Int, val height: Int)

object ScreenMetricsHelper {

    /**
     * Resolves physical display metrics accounting for system window insets,
     * cutouts, and orientation across standard devices and foldables.
     */
    fun getScreenDimensions(context: Context): ScreenDimensions {
        return try {
            val metrics = WindowMetricsCalculator.getOrCreate()
                .computeMaximumWindowMetrics(context)
            val bounds = metrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            if (width > 0 && height > 0) {
                ScreenDimensions(width, height)
            } else {
                getFallbackDimensions(context)
            }
        } catch (_: Exception) {
            getFallbackDimensions(context)
        }
    }

    private fun getFallbackDimensions(context: Context? = null): ScreenDimensions {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context != null) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val bounds = wm?.maximumWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    return ScreenDimensions(bounds.width(), bounds.height())
                }
            } catch (_: Exception) {}
        }
        val dm = Resources.getSystem().displayMetrics
        return ScreenDimensions(dm.widthPixels, dm.heightPixels)
    }
}
