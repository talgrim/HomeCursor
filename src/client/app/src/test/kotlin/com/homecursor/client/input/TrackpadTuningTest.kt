package com.homecursor.client.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackpadTuningTest {
    @Test
    fun forDensity_scalesPixelValuesWithDensity() {
        val normal = TrackpadTuning.forDensity(1f)
        val dense = TrackpadTuning.forDensity(3f)

        assertEquals(normal.gestures.touchSlop * 3, dense.gestures.touchSlop, 0.0001f)
        assertEquals(normal.acceleration.thresholdPxPerMs * 3, dense.acceleration.thresholdPxPerMs, 0.0001f)
    }

    @Test
    fun forDensity_gainPerPixelPerMsShrinksWithDensity() {
        val normal = TrackpadTuning.forDensity(1f)
        val dense = TrackpadTuning.forDensity(3f)

        assertEquals(normal.acceleration.slope / 3, dense.acceleration.slope, 0.0001f)
    }

    @Test
    fun forDensity_samePhysicalSwipe_feelsTheSameOnAnyDensity() {
        // A swipe of 100 dp in 100 ms, measured in each screen's pixels.
        val gains = listOf(1f, 2f, 3.5f).map { density ->
            val acceleration = PointerAcceleration(TrackpadTuning.forDensity(density).acceleration)
            acceleration.gain(100f * density, 0f, 100)
        }

        assertEquals(gains[0], gains[1], 0.0001f)
        assertEquals(gains[0], gains[2], 0.0001f)
    }

    @Test
    fun forDensity_scrollUnitsPerPixelShrinksWithDensity() {
        // 16 dp of travel scroll one wheel notch (120 units) at any density.
        for (density in listOf(1f, 2.625f, 3f)) {
            val tuning = TrackpadTuning.forDensity(density)
            assertEquals(120f, 16f * density * tuning.scrollUnitsPerPixel, 0.001f)
        }
    }

    @Test
    fun forDensity_timesDoNotDependOnDensity() {
        assertEquals(TrackpadTuning.forDensity(1f).gestures.tapTimeoutMillis, TrackpadTuning.forDensity(3f).gestures.tapTimeoutMillis)
        assertEquals(TrackpadTuning.forDensity(1f).flushIntervalMillis, TrackpadTuning.forDensity(3f).flushIntervalMillis)
    }

    @Test
    fun forDensity_pointerRunsAtHalfOfTheFingerTravelAtTheDefaultSpeedSetting() {
        // The speed setting reads 1x at a comfortable speed; that speed is half of the finger's travel.
        assertEquals(0.5f, TrackpadTuning.forDensity(1f).pointerSpeedScale, 0f)
        assertEquals(0.5f, TrackpadTuning.forDensity(3f).pointerSpeedScale, 0f)
    }

    @Test
    fun forDensity_tapWaitsAtMostAQuarterSecondForADrag() {
        assertEquals(250L, TrackpadTuning.forDensity(2f).gestures.doubleTapWindowMillis)
    }

    @Test
    fun forDensity_nonPositive_throws() {
        assertThrows(IllegalArgumentException::class.java) { TrackpadTuning.forDensity(0f) }
        assertThrows(IllegalArgumentException::class.java) { TrackpadTuning.forDensity(-1f) }
    }

    @Test
    fun constructor_invalidValues_throw() {
        val valid = TrackpadTuning.forDensity(1f)

        assertThrows(IllegalArgumentException::class.java) { valid.copy(scrollUnitsPerPixel = 0f) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(flushIntervalMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(pointerSpeedScale = 0f) }
    }
}
