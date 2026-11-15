package com.homecursor.client.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerAccelerationTest {
    private val acceleration = PointerAcceleration(PointerAcceleration.Config(thresholdPxPerMs = 0.5f, slope = 1f, maxGain = 3f))

    @Test
    fun gain_slowMovement_isOne() {
        // 4 px in 16 ms = 0.25 px/ms, below the threshold
        assertEquals(1f, acceleration.gain(4f, 0f, 16), 0.0001f)
    }

    @Test
    fun gain_exactlyAtThreshold_isOne() {
        assertEquals(1f, acceleration.gain(8f, 0f, 16), 0.0001f)
    }

    @Test
    fun gain_aboveThreshold_growsWithSpeed() {
        // 1 px/ms: 0.5 above the threshold, slope 1
        assertEquals(1.5f, acceleration.gain(16f, 0f, 16), 0.0001f)
        // 1.5 px/ms
        assertEquals(2f, acceleration.gain(24f, 0f, 16), 0.0001f)
    }

    @Test
    fun gain_veryFast_isCappedAtMaxGain() {
        assertEquals(3f, acceleration.gain(500f, 0f, 16), 0.0001f)
    }

    @Test
    fun gain_usesTheDistanceNotTheAxis() {
        // (12, 16) is 20 px long: 20 px in 16 ms = 1.25 px/ms
        assertEquals(1.75f, acceleration.gain(12f, 16f, 16), 0.0001f)
        assertEquals(acceleration.gain(20f, 0f, 16), acceleration.gain(12f, 16f, 16), 0.0001f)
    }

    @Test
    fun gain_negativeDistance_countsAsSpeed() {
        assertEquals(acceleration.gain(16f, 0f, 16), acceleration.gain(-16f, 0f, 16), 0.0001f)
    }

    @Test
    fun gain_zeroOrNegativeElapsed_usesAMinimumInsteadOfDividingByZero() {
        val gain = acceleration.gain(2f, 0f, 0)

        assertTrue(gain.isFinite())
        // 2 px over the 4 ms floor = 0.5 px/ms, right at the threshold
        assertEquals(1f, gain, 0.0001f)
        assertEquals(gain, acceleration.gain(2f, 0f, -5), 0.0001f)
    }

    @Test
    fun gain_noMovement_isOne() {
        assertEquals(1f, acceleration.gain(0f, 0f, 16), 0.0001f)
    }

    @Test
    fun config_invalidValues_throw() {
        assertThrows(IllegalArgumentException::class.java) { PointerAcceleration.Config(thresholdPxPerMs = -1f) }
        assertThrows(IllegalArgumentException::class.java) { PointerAcceleration.Config(slope = -1f) }
        assertThrows(IllegalArgumentException::class.java) { PointerAcceleration.Config(maxGain = 0.5f) }
    }
}
