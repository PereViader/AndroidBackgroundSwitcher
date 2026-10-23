package com.pereviader.backgroundswitcher.core.crop

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs

class CenterCropEngineTest {

    @Test
    fun `landscape image on portrait screen scales to cover height and centers width`() {
        // Landscape photo (4000x3000 = 4:3) on modern phone screen (1080x2400 = 9:20)
        val sourceWidth = 4000
        val sourceHeight = 3000
        val targetWidth = 1080
        val targetHeight = 2400

        val crop = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        // Height must match exactly
        assertThat(crop.scaledHeight).isEqualTo(targetHeight)
        // Scaled width must be larger than target width
        assertThat(crop.scaledWidth).isGreaterThan(targetWidth)
        // Offset Y must be 0 because height fits exactly
        assertThat(crop.offsetY).isEqualTo(0)
        // Offset X must be negative and centered
        assertThat(crop.offsetX).isLessThan(0)
        // The crop must be centered symmetrically within 1px rounding tolerance
        val leftCropped = abs(crop.offsetX)
        val rightCropped = crop.scaledWidth - targetWidth - leftCropped
        assertThat(abs(leftCropped - rightCropped)).isAtMost(1)
    }

    @Test
    fun `tall portrait image on wider screen scales to cover width and centers height`() {
        // Tall portrait photo (1080x2400) on standard screen (1080x1920)
        val sourceWidth = 1080
        val sourceHeight = 2400
        val targetWidth = 1080
        val targetHeight = 1920

        val crop = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        // Width must match exactly
        assertThat(crop.scaledWidth).isEqualTo(targetWidth)
        // Scaled height must be larger than target height
        assertThat(crop.scaledHeight).isGreaterThan(targetHeight)
        // Offset X must be 0
        assertThat(crop.offsetX).isEqualTo(0)
        // Offset Y must be centered
        assertThat(crop.offsetY).isLessThan(0)
        val topCropped = abs(crop.offsetY)
        val bottomCropped = crop.scaledHeight - targetHeight - topCropped
        assertThat(abs(topCropped - bottomCropped)).isAtMost(1)
    }

    @Test
    fun `identical aspect ratio scales with zero offset`() {
        val sourceWidth = 1920
        val sourceHeight = 1080
        val targetWidth = 1920
        val targetHeight = 1080

        val crop = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        assertThat(crop.scale).isEqualTo(1.0f)
        assertThat(crop.scaledWidth).isEqualTo(targetWidth)
        assertThat(crop.scaledHeight).isEqualTo(targetHeight)
        assertThat(crop.offsetX).isEqualTo(0)
        assertThat(crop.offsetY).isEqualTo(0)
    }

    @Test
    fun `custom horizontal crop positioning aligns left, right, or center as requested`() {
        val sourceWidth = 4000
        val sourceHeight = 3000
        val targetWidth = 1080
        val targetHeight = 2400

        // cropX = 0f (left-aligned)
        val leftCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = 0.0f
        )
        assertThat(leftCrop.offsetX).isEqualTo(0)
        assertThat(leftCrop.scaledWidth).isGreaterThan(targetWidth)

        // cropX = 1f (right-aligned)
        val rightCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = 1.0f
        )
        val excessWidth = rightCrop.scaledWidth - targetWidth
        assertThat(rightCrop.offsetX).isEqualTo(-excessWidth)

        // cropX = null defaults to 0.5f (centered)
        val defaultCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = null
        )
        val centeredCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = 0.5f
        )
        assertThat(defaultCrop.offsetX).isEqualTo(centeredCrop.offsetX)
        assertThat(defaultCrop.offsetY).isEqualTo(centeredCrop.offsetY)
    }

    @Test
    fun `custom vertical crop positioning aligns top, bottom, or center as requested`() {
        val sourceWidth = 1080
        val sourceHeight = 2400
        val targetWidth = 1080
        val targetHeight = 1920

        // cropY = 0f (top-aligned)
        val topCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropY = 0.0f
        )
        assertThat(topCrop.offsetY).isEqualTo(0)

        // cropY = 1f (bottom-aligned)
        val bottomCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropY = 1.0f
        )
        val excessHeight = bottomCrop.scaledHeight - targetHeight
        assertThat(bottomCrop.offsetY).isEqualTo(-excessHeight)

        // cropY = null defaults to 0.5f (centered)
        val defaultCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropY = null
        )
        val centeredCrop = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropY = 0.5f
        )
        assertThat(defaultCrop.offsetY).isEqualTo(centeredCrop.offsetY)
    }

    @Test
    fun `crop positions outside 0 to 1 are strictly clamped to cover screen bounds`() {
        val sourceWidth = 4000
        val sourceHeight = 2000
        val targetWidth = 1080
        val targetHeight = 2400

        val overClamped = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = 2.5f, cropY = -1.5f
        )
        val excessWidth = overClamped.scaledWidth - targetWidth

        // offsetX must never exceed [-excessWidth, 0] so screen bounds are always completely covered
        assertThat(overClamped.offsetX).isAtLeast(-excessWidth)
        assertThat(overClamped.offsetX).isAtMost(0)
        assertThat(overClamped.offsetY).isEqualTo(0)
    }

    @Test
    fun `negative or zero dimensions throw IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            CropCalculation.calculateCoverCrop(0, 100, 100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CropCalculation.calculateCoverCrop(100, -5, 100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CropCalculation.calculateCoverCrop(100, 100, 0, 100)
        }
    }

    @Test
    fun `zoomed crop preserves aspect ratio and completely covers screen`() {
        val sourceWidth = 4000
        val sourceHeight = 3000
        val targetWidth = 1080
        val targetHeight = 2400

        val baseCrop = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            zoom = 1.0f
        )

        val zoomedCrop = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            zoom = 2.0f
        )

        // Scale should be doubled
        assertThat(zoomedCrop.scale).isWithin(0.01f).of(baseCrop.scale * 2.0f)
        // Scaled dimensions must both be strictly >= target
        assertThat(zoomedCrop.scaledWidth).isAtLeast(targetWidth)
        assertThat(zoomedCrop.scaledHeight).isAtLeast(targetHeight)

        // Aspect ratio must be preserved
        val srcAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val scaledAspect = zoomedCrop.scaledWidth.toDouble() / zoomedCrop.scaledHeight.toDouble()
        assertThat(scaledAspect).isWithin(0.01).of(srcAspect)

        // Target bounds must be completely covered:
        // left offset <= 0, right edge >= targetWidth
        assertThat(zoomedCrop.offsetX).isAtMost(0)
        assertThat(zoomedCrop.offsetX + zoomedCrop.scaledWidth).isAtLeast(targetWidth)
        // top offset <= 0, bottom edge >= targetHeight
        assertThat(zoomedCrop.offsetY).isAtMost(0)
        assertThat(zoomedCrop.offsetY + zoomedCrop.scaledHeight).isAtLeast(targetHeight)
    }

    @Test
    fun `zoom less than 1 is clamped to 1 so screen is never letterboxed`() {
        val sourceWidth = 1920
        val sourceHeight = 1080
        val targetWidth = 1080
        val targetHeight = 2400

        val cropUnderZoom = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            zoom = 0.5f // Attempting to zoom out below cover
        )

        val cropDefault = CropCalculation.calculateCoverCrop(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            zoom = 1.0f
        )

        assertThat(cropUnderZoom.scale).isEqualTo(cropDefault.scale)
        assertThat(cropUnderZoom.scaledWidth).isAtLeast(targetWidth)
        assertThat(cropUnderZoom.scaledHeight).isAtLeast(targetHeight)
    }

    @Test
    fun `zoomed crop allows 2D panning when both dimensions have excess`() {
        val sourceWidth = 1000
        val sourceHeight = 1000
        val targetWidth = 1000
        val targetHeight = 2000

        // At zoom = 2.5x, both X and Y have large excess
        val cropTopLeft = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = 0f, cropY = 0f, zoom = 2.5f
        )
        assertThat(cropTopLeft.offsetX).isEqualTo(0)
        assertThat(cropTopLeft.offsetY).isEqualTo(0)

        val cropBottomRight = CropCalculation.calculateCoverCrop(
            sourceWidth, sourceHeight, targetWidth, targetHeight, cropX = 1f, cropY = 1f, zoom = 2.5f
        )
        val excessX = cropBottomRight.scaledWidth - targetWidth
        val excessY = cropBottomRight.scaledHeight - targetHeight

        assertThat(cropBottomRight.offsetX).isEqualTo(-excessX)
        assertThat(cropBottomRight.offsetY).isEqualTo(-excessY)
    }

    @Test
    fun `preview scaling produces identical visible source subrectangle as physical screen wallpaper`() {
        val sourceWidth = 4032
        val sourceHeight = 3024
        val screenWidth = 1080
        val screenHeight = 2400

        // Preview container has exact screen aspect ratio at downscaled resolution
        val scaleFactor = 0.35f
        val previewWidth = screenWidth * scaleFactor
        val previewHeight = screenHeight * scaleFactor

        for (cropX in listOf(0.0f, 0.2f, 0.5f, 0.85f, 1.0f)) {
            val screenCrop = CropCalculation.calculateCoverCrop(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                targetWidth = screenWidth,
                targetHeight = screenHeight,
                cropX = cropX
            )

            // Scaled offsets in preview
            val previewTotalScale = screenCrop.scale * scaleFactor
            val previewTotalOffsetX = screenCrop.offsetX * scaleFactor

            // Check source coordinates of the left and right visible edges:
            val screenSrcLeft = -screenCrop.offsetX / screenCrop.scale
            val screenSrcRight = (screenWidth - screenCrop.offsetX) / screenCrop.scale

            val previewSrcLeft = -previewTotalOffsetX / previewTotalScale
            val previewSrcRight = (previewWidth - previewTotalOffsetX) / previewTotalScale

            // The visible window on source bitmap must match perfectly
            assertThat(abs(screenSrcLeft - previewSrcLeft)).isLessThan(0.001f)
            assertThat(abs(screenSrcRight - previewSrcRight)).isLessThan(0.001f)
        }
    }

    @Test
    fun `vertical crop preview scaling produces identical visible source subrectangle as physical screen wallpaper`() {
        val sourceWidth = 1200
        val sourceHeight = 3600
        val screenWidth = 1080
        val screenHeight = 2400

        val scaleFactor = 0.4f
        val previewWidth = screenWidth * scaleFactor
        val previewHeight = screenHeight * scaleFactor

        for (cropY in listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)) {
            val screenCrop = CropCalculation.calculateCoverCrop(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                targetWidth = screenWidth,
                targetHeight = screenHeight,
                cropY = cropY
            )

            val previewTotalScale = screenCrop.scale * scaleFactor
            val previewTotalOffsetY = screenCrop.offsetY * scaleFactor

            val screenSrcTop = -screenCrop.offsetY / screenCrop.scale
            val screenSrcBottom = (screenHeight - screenCrop.offsetY) / screenCrop.scale

            val previewSrcTop = -previewTotalOffsetY / previewTotalScale
            val previewSrcBottom = (previewHeight - previewTotalOffsetY) / previewTotalScale

            assertThat(abs(screenSrcTop - previewSrcTop)).isLessThan(0.001f)
            assertThat(abs(screenSrcBottom - previewSrcBottom)).isLessThan(0.001f)
        }
    }
}
