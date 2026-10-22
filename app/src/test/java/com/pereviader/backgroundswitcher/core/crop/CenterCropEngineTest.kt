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
}
