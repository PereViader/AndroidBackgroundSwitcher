package com.pereviader.backgroundswitcher.core.crop

import kotlin.math.max
import kotlin.math.roundToInt

data class CropDimensions(
    val scale: Float,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val offsetX: Int,
    val offsetY: Int
)

object CropCalculation {

    /**
     * Calculates the CSS "object-fit: cover" transformation for an image of dimensions
     * (sourceWidth, sourceHeight) to fill target dimensions (targetWidth, targetHeight).
     *
     * In cover behavior:
     * 1. The aspect ratio is strictly preserved.
     * 2. The scaled image covers the entire target area (no empty bars / letterboxing).
     * 3. The image is centered along the excess dimension (cropped equally on top/bottom or left/right).
     */
    fun calculateCoverCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): CropDimensions {
        require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
        require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive" }

        val scaleX = targetWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = targetHeight.toFloat() / sourceHeight.toFloat()

        // Choose the larger scale factor to ensure the entire target is covered
        val scale = max(scaleX, scaleY)

        val scaledWidth = (sourceWidth * scale).roundToInt()
        val scaledHeight = (sourceHeight * scale).roundToInt()

        // Center offsets (negative or zero when centered)
        val offsetX = (targetWidth - scaledWidth) / 2
        val offsetY = (targetHeight - scaledHeight) / 2

        return CropDimensions(
            scale = scale,
            scaledWidth = scaledWidth,
            scaledHeight = scaledHeight,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }
}
