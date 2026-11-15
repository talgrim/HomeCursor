package com.homecursor.client.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAccumulatorTest {
    private val accumulator = MotionAccumulator()

    @Test
    fun drain_nothingAdded_returnsZero() {
        assertTrue(accumulator.drain().isZero)
    }

    @Test
    fun drain_wholeUnits_areReturnedAndCleared() {
        accumulator.add(3f, -4f)

        assertEquals(Motion(3, -4), accumulator.drain())
        assertTrue(accumulator.drain().isZero)
    }

    @Test
    fun drain_fractionsBelowOne_carryOverUntilTheyAddUp() {
        accumulator.add(0.4f, 0f)
        assertTrue(accumulator.drain().isZero)
        accumulator.add(0.4f, 0f)
        assertTrue(accumulator.drain().isZero)
        accumulator.add(0.4f, 0f)

        assertEquals(Motion(1, 0), accumulator.drain())
    }

    @Test
    fun drain_negativeValues_truncateTowardZeroAndKeepTheRemainder() {
        accumulator.add(-1.7f, 0f)
        assertEquals(Motion(-1, 0), accumulator.drain())

        accumulator.add(-0.4f, 0f) // -0.7 left over + -0.4 = -1.1
        assertEquals(Motion(-1, 0), accumulator.drain())
    }

    @Test
    fun drain_axesAreIndependent() {
        accumulator.add(0.6f, 2.5f)

        assertEquals(Motion(0, 2), accumulator.drain())
        accumulator.add(0.6f, 0.6f)
        assertEquals(Motion(1, 1), accumulator.drain())
    }

    @Test
    fun drain_moreThanInt16_isSpreadOverSeveralDrains() {
        accumulator.add(70_000f, -70_000f)

        assertEquals(Motion(32767, -32768), accumulator.drain())
        assertEquals(Motion(32767, -32768), accumulator.drain())
        assertEquals(Motion(4466, -4464), accumulator.drain())
        assertTrue(accumulator.drain().isZero)
    }

    @Test
    fun drain_manySmallSteps_loseNothing() {
        var emittedX = 0
        var emittedY = 0
        repeat(1000) {
            accumulator.add(0.37f, -0.81f)
            val motion = accumulator.drain()
            emittedX += motion.dx
            emittedY += motion.dy
        }

        // 1000 * 0.37 = 370 and 1000 * -0.81 = -810, to within the one unit still held as remainder.
        assertEquals(370f, emittedX.toFloat(), 1f)
        assertEquals(-810f, emittedY.toFloat(), 1f)
    }

    @Test
    fun reset_forgetsLeftovers() {
        accumulator.add(0.9f, 0.9f)

        accumulator.reset()
        accumulator.add(0.2f, 0.2f)

        assertTrue(accumulator.drain().isZero)
    }

    @Test
    fun motion_isZero_onlyWhenBothAxesAreZero() {
        assertTrue(Motion(0, 0).isZero)
        assertFalse(Motion(1, 0).isZero)
        assertFalse(Motion(0, -1).isZero)
    }
}
