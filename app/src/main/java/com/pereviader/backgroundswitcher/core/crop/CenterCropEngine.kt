package com.pereviader.backgroundswitcher.core.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build

object CenterCropEngine {

    /**
     * Transforms a source [Bitmap] to an exact target resolution [targetWidth] x [targetHeight]
     * using cover fitting with optional [cropX]/[cropY] offsets and [zoom].
     *
     * Returns a new [Bitmap] matching targetWidth and targetHeight with aspect ratio preserved
     * and excess borders cleanly cropped off.
     */
    fun createCoverBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        cropX: Float? = null,
        cropY: Float? = null,
        zoom: Float? = null
    ): Bitmap {
        val calc = CropCalculation.calculateCoverCrop(
            sourceWidth = source.width,
            sourceHeight = source.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            cropX = cropX,
            cropY = cropY,
            zoom = zoom
        )

        val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.colorSpace != null) {
            Bitmap.createBitmap(null, targetWidth, targetHeight, Bitmap.Config.ARGB_8888, false, source.colorSpace!!)
        } else {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
                setHasAlpha(false)
            }
        }

        val canvas = Canvas(output)

        val matrix = Matrix().apply {
            postScale(calc.scale, calc.scale)
            postTranslate(calc.offsetX.toFloat(), calc.offsetY.toFloat())
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, matrix, paint)

        return output
    }
}
