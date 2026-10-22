package com.pereviader.backgroundswitcher.core.crop

import android.content.Context
import android.content.res.Resources
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
                .computeCurrentWindowMetrics(context)
            val bounds = metrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            if (width > 0 && height > 0) {
                ScreenDimensions(width, height)
            } else {
                getFallbackDimensions()
            }
        } catch (_: Exception) {
            getFallbackDimensions()
        }
    }

    private fun getFallbackDimensions(): ScreenDimensions {
        val dm = Resources.getSystem().displayMetrics
        return ScreenDimensions(dm.widthPixels, dm.heightPixels)
    }
}
