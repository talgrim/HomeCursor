package com.homecursor.client.input

import kotlin.math.hypot

/**
 * Speed-based pointer gain: slow finger movement maps 1:1 for precise placement, fast movement is amplified so a
 * short swipe crosses the screen.
 *
 * Below [Config.thresholdPxPerMs] the gain is 1. Above it the gain grows by [Config.slope] per px/ms, up to
 * [Config.maxGain]. All values are in pixels, so the UI should scale them by the screen density.
 */
class PointerAcceleration(private val config: Config = Config()) {
    data class Config(
        val thresholdPxPerMs: Float = 0.4f,
        val slope: Float = 0.8f,
        val maxGain: Float = 3f,
    ) {
        init {
            require(thresholdPxPerMs >= 0f) { "thresholdPxPerMs must not be negative." }
            require(slope >= 0f) { "slope must not be negative." }
            require(maxGain >= 1f) { "maxGain must be at least 1." }
        }
    }

    /** The factor to multiply a finger travel of ([dx], [dy]) pixels that took [elapsedMillis] by. Always 1 or more. */
    fun gain(dx: Float, dy: Float, elapsedMillis: Long): Float {
        // Touch events can arrive with almost no time between them; without a floor that would read as a huge speed.
        val speed = hypot(dx, dy) / elapsedMillis.coerceAtLeast(MIN_ELAPSED_MILLIS)
        val excess = (speed - config.thresholdPxPerMs).coerceAtLeast(0f)
        return (1f + excess * config.slope).coerceAtMost(config.maxGain)
    }

    private companion object {
        const val MIN_ELAPSED_MILLIS = 4L
    }
}
