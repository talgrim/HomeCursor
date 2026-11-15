package com.homecursor.client.input

/**
 * Every pixel-based and time-based constant of the trackpad in one place. The gesture, acceleration and scroll
 * logic works in pixels, but a pixel is a different distance on every phone, so the values are defined in dp and
 * converted by [forDensity]. These are starting values to tune on a real device.
 */
data class TrackpadTuning(
    val gestures: TrackpadGestureInterpreter.Config,
    val acceleration: PointerAcceleration.Config,
    /** Scroll units (1/120 of a wheel notch) per pixel of two-finger travel, before the direction is applied. */
    val scrollUnitsPerPixel: Float,
    /** Pointer and scroll motion is sent at most once per this many milliseconds. */
    val flushIntervalMillis: Long,
) {
    init {
        require(scrollUnitsPerPixel > 0f) { "scrollUnitsPerPixel must be positive." }
        require(flushIntervalMillis > 0) { "flushIntervalMillis must be positive." }
    }

    companion object {
        private const val TOUCH_SLOP_DP = 6f
        private const val TAP_TIMEOUT_MILLIS = 250L
        private const val DOUBLE_TAP_WINDOW_MILLIS = 300L

        /** Below this finger speed the pointer follows the finger 1:1. */
        private const val ACCELERATION_THRESHOLD_DP_PER_MS = 0.15f
        private const val ACCELERATION_SLOPE_PER_DP_PER_MS = 2.4f
        private const val ACCELERATION_MAX_GAIN = 3f

        /** Finger travel that scrolls one wheel notch. */
        private const val SCROLL_DP_PER_NOTCH = 16f
        private const val UNITS_PER_NOTCH = 120f

        /** About 120 Hz: smooth for the eye, and few enough datagrams for any Wi-Fi. */
        private const val FLUSH_INTERVAL_MILLIS = 8L

        /** @param density the screen density, pixels per dp (`displayMetrics.density`). */
        fun forDensity(density: Float): TrackpadTuning {
            require(density > 0f) { "density must be positive." }
            return TrackpadTuning(
                gestures = TrackpadGestureInterpreter.Config(
                    touchSlop = TOUCH_SLOP_DP * density,
                    tapTimeoutMillis = TAP_TIMEOUT_MILLIS,
                    doubleTapWindowMillis = DOUBLE_TAP_WINDOW_MILLIS,
                ),
                acceleration = PointerAcceleration.Config(
                    thresholdPxPerMs = ACCELERATION_THRESHOLD_DP_PER_MS * density,
                    // gain per px/ms is the gain per dp/ms divided by pixels per dp
                    slope = ACCELERATION_SLOPE_PER_DP_PER_MS / density,
                    maxGain = ACCELERATION_MAX_GAIN,
                ),
                scrollUnitsPerPixel = UNITS_PER_NOTCH / (SCROLL_DP_PER_NOTCH * density),
                flushIntervalMillis = FLUSH_INTERVAL_MILLIS,
            )
        }
    }
}
