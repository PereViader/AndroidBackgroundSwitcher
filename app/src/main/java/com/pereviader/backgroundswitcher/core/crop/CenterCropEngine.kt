package com.pereviader.backgroundswitcher.core.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

object CenterCropEngine {

    /**
     * Transforms a source [Bitmap] to an exact target resolution [targetWidth] x [targetHeight]
     * using centered "cover" fitting.
     *
     * Returns a new [Bitmap] matching targetWidth and targetHeight with aspect ratio preserved
     * and excess borders cleanly cropped off symmetrically.
     */
    fun createCoverBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val calc = CropCalculation.calculateCoverCrop(
            sourceWidth = source.width,
            sourceHeight = source.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
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
