package com.homecursor.client.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackpadSettingsTest {
    @Test
    fun defaults_areNormalSensitivityAndTraditionalScroll() {
        val settings = TrackpadSettings()

        assertEquals(1f, settings.sensitivity, 0f)
        assertFalse(settings.naturalScroll)
    }

    @Test
    fun constructor_limitsAreAccepted() {
        assertEquals(0.5f, TrackpadSettings(sensitivity = 0.5f).sensitivity, 0f)
        assertEquals(3f, TrackpadSettings(sensitivity = 3f).sensitivity, 0f)
    }

    @Test
    fun constructor_outOfRangeSensitivity_throws() {
        assertThrows(IllegalArgumentException::class.java) { TrackpadSettings(sensitivity = 0.49f) }
        assertThrows(IllegalArgumentException::class.java) { TrackpadSettings(sensitivity = 3.01f) }
        assertThrows(IllegalArgumentException::class.java) { TrackpadSettings(sensitivity = Float.NaN) }
    }

    @Test
    fun coerced_inRange_keepsValues() {
        assertEquals(TrackpadSettings(2.25f, true), TrackpadSettings.coerced(2.25f, true))
    }

    @Test
    fun coerced_belowMinimum_usesMinimum() {
        assertEquals(0.5f, TrackpadSettings.coerced(0f, false).sensitivity, 0f)
        assertEquals(0.5f, TrackpadSettings.coerced(-4f, false).sensitivity, 0f)
        assertEquals(0.5f, TrackpadSettings.coerced(Float.NEGATIVE_INFINITY, false).sensitivity, 0f)
    }

    @Test
    fun coerced_aboveMaximum_usesMaximum() {
        assertEquals(3f, TrackpadSettings.coerced(10f, false).sensitivity, 0f)
        assertEquals(3f, TrackpadSettings.coerced(Float.POSITIVE_INFINITY, false).sensitivity, 0f)
    }

    @Test
    fun coerced_notANumber_usesDefault() {
        assertEquals(1f, TrackpadSettings.coerced(Float.NaN, true).sensitivity, 0f)
    }

    @Test
    fun copy_withOutOfRangeSensitivity_throws() {
        assertThrows(IllegalArgumentException::class.java) { TrackpadSettings().copy(sensitivity = 5f) }
    }
}
