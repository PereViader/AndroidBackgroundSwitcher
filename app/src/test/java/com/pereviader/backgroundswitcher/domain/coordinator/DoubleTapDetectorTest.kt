package com.pereviader.backgroundswitcher.domain.coordinator

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class DoubleTapDetectorTest {

    private lateinit var detector: DoubleTapDetector

    @Before
    fun setUp() {
        detector = DoubleTapDetector(
            maxDelayMillis = 500L,
            minIntervalMillis = 80L,
            maxSlopPixels = 500
        )
    }

    @Test
    fun `single tap does not trigger double tap`() {
        val result = detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)
        assertThat(result).isFalse()
    }

    @Test
    fun `two taps within standard window trigger double tap`() {
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()

        // Second tap 350ms later
        assertThat(detector.onUserTap(x = 105, y = 205, nowMillis = 1350L)).isTrue()
    }

    @Test
    fun `two taps with cadence up to 500ms trigger double tap`() {
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()

        // Second tap 480ms later
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1480L)).isTrue()
    }

    @Test
    fun `two taps exceeding maxDelayMillis do not trigger double tap but reset candidate`() {
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()

        // Second tap arrives 550ms later (>500ms)
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1550L)).isFalse()

        // Third tap arrives 250ms after the second tap -> triggers double tap with second tap
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1800L)).isTrue()
    }

    @Test
    fun `two taps exceeding maxSlopPixels do not trigger double tap and reset candidate`() {
        // First tap at (100, 200)
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()

        // Second tap at (700, 800) 300ms later (distance > 500px)
        assertThat(detector.onUserTap(x = 700, y = 800, nowMillis = 1300L)).isFalse()

        // Third tap at (710, 810) 250ms later (near second tap) -> triggers double tap
        assertThat(detector.onUserTap(x = 710, y = 810, nowMillis = 1550L)).isTrue()
    }

    @Test
    fun `duplicate tap event arriving within minIntervalMillis is ignored without corrupting state`() {
        // First physical tap produces event from onTouchEvent
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()

        // Duplicate event from onCommand arrives 30ms later
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1030L)).isFalse()

        // Second physical tap arrives 400ms after the first tap
        assertThat(detector.onUserTap(x = 105, y = 205, nowMillis = 1400L)).isTrue()
    }

    @Test
    fun `duplicate tap event arriving within duplicate window at near coordinates is ignored`() {
        // First physical tap
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()

        // Duplicate IPC command arrives 110ms later at near coordinates (within 80px)
        assertThat(detector.onUserTap(x = 102, y = 201, nowMillis = 1110L)).isFalse()

        // Second physical tap arrives 350ms later
        assertThat(detector.onUserTap(x = 105, y = 205, nowMillis = 1460L)).isTrue()
    }

    @Test
    fun `duplicate event immediately following double tap is ignored and does not start new sequence`() {
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1350L)).isTrue()

        // Duplicate event arriving 25ms after the double tap
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1375L)).isFalse()

        // A single subsequent tap 500ms later should NOT trigger a false double tap
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1875L)).isFalse()
    }

    @Test
    fun `two separate consecutive double-tap gestures succeed`() {
        // First double tap
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1300L)).isTrue()

        // Second double tap later
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 2500L)).isFalse()
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 2800L)).isTrue()
    }

    @Test
    fun `reset clears candidate tap and duplicate tracker`() {
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1000L)).isFalse()
        detector.reset()

        // Subsequent tap within 300ms should NOT trigger double tap because of reset
        assertThat(detector.onUserTap(x = 100, y = 200, nowMillis = 1300L)).isFalse()
    }
}
