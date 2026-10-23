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
     * 3. When [cropX] and [cropY] are null, the image is centered along the excess dimension.
     *    When provided (normalized 0.0f..1.0f), the excess is offset accordingly while strictly
     *    ensuring the target bounds are completely covered.
     */
    fun calculateCoverCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        cropX: Float? = null,
        cropY: Float? = null
    ): CropDimensions {
        require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
        require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive" }

        val scaleX = targetWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = targetHeight.toFloat() / sourceHeight.toFloat()

        // Choose the larger scale factor to ensure the entire target is covered
        val scale = max(scaleX, scaleY)

        val scaledWidth = max(targetWidth, (sourceWidth * scale).roundToInt())
        val scaledHeight = max(targetHeight, (sourceHeight * scale).roundToInt())

        val excessX = max(0, scaledWidth - targetWidth)
        val excessY = max(0, scaledHeight - targetHeight)

        val normalizedX = (cropX ?: 0.5f).coerceIn(0f, 1f)
        val normalizedY = (cropY ?: 0.5f).coerceIn(0f, 1f)

        // Offsets are clamped to ensure target bounds are completely covered at all times
        val offsetX = if (excessX > 0) {
            (-excessX * normalizedX).roundToInt().coerceIn(-excessX, 0)
        } else {
            0
        }

        val offsetY = if (excessY > 0) {
            (-excessY * normalizedY).roundToInt().coerceIn(-excessY, 0)
        } else {
            0
        }

        return CropDimensions(
            scale = scale,
            scaledWidth = scaledWidth,
            scaledHeight = scaledHeight,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }
}
