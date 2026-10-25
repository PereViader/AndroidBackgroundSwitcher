package com.pereviader.backgroundswitcher.domain.coordinator

/**
 * Pure Kotlin detector for double-tap gestures with zero Android framework dependencies.
 *
 * Tracks spatial and temporal proximity between taps, relaxes cadences for desktop usability,
 * enforces touch slop, and filters out duplicate/bouncing events from multiple simultaneous sources
 * (such as simultaneous onTouchEvent and onCommand triggers).
 */
class DoubleTapDetector(
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    val maxSlopPixels: Int = DEFAULT_MAX_SLOP_PIXELS
) {
    companion object {
        const val DEFAULT_MAX_DELAY_MILLIS = 500L
        const val DEFAULT_MIN_INTERVAL_MILLIS = 80L
        const val DEFAULT_MAX_SLOP_PIXELS = 500
        const val DUPLICATE_SLOP_PIXELS = 80
        const val DUPLICATE_WINDOW_MILLIS = 150L
    }

    private var lastCandidateTapTime = 0L
    private var lastCandidateX = 0
    private var lastCandidateY = 0

    private var lastAnyTapTime = 0L
    private var lastAnyTapX = 0
    private var lastAnyTapY = 0

    /**
     * Process an incoming tap event.
     *
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     * @param nowMillis Timestamp of the event in milliseconds
     * @return true if a valid double-tap sequence is detected, false otherwise.
     */
    fun onUserTap(x: Int, y: Int, nowMillis: Long): Boolean {
        val timeSinceAnyTap = if (lastAnyTapTime > 0L) nowMillis - lastAnyTapTime else Long.MAX_VALUE
        val dxAny = x - lastAnyTapX
        val dyAny = y - lastAnyTapY
        val distSqAny = dxAny * dxAny + dyAny * dyAny

        // Filter out rapid duplicate or bouncing events from multiple simultaneous sources
        // (e.g. onTouchEvent and onCommand firing for the exact same physical tap)
        val isDuplicate = timeSinceAnyTap < minIntervalMillis ||
            (timeSinceAnyTap < DUPLICATE_WINDOW_MILLIS && distSqAny <= DUPLICATE_SLOP_PIXELS * DUPLICATE_SLOP_PIXELS)

        if (isDuplicate) {
            return false
        }

        lastAnyTapTime = nowMillis
        lastAnyTapX = x
        lastAnyTapY = y

        val timeDiff = if (lastCandidateTapTime > 0L) nowMillis - lastCandidateTapTime else Long.MAX_VALUE
        val dxCandidate = x - lastCandidateX
        val dyCandidate = y - lastCandidateY
        val distSqCandidate = dxCandidate * dxCandidate + dyCandidate * dyCandidate
        val withinSlop = distSqCandidate <= maxSlopPixels * maxSlopPixels

        return if (timeDiff in minIntervalMillis..maxDelayMillis && withinSlop) {
            // Valid double-tap sequence completed within time window and spatial slop
            lastCandidateTapTime = 0L
            lastCandidateX = 0
            lastCandidateY = 0
            true
        } else {
            // First tap of a new potential double-tap sequence
            lastCandidateTapTime = nowMillis
            lastCandidateX = x
            lastCandidateY = y
            false
        }
    }

    fun reset() {
        lastCandidateTapTime = 0L
        lastCandidateX = 0
        lastCandidateY = 0
        lastAnyTapTime = 0L
        lastAnyTapX = 0
        lastAnyTapY = 0
    }
}
