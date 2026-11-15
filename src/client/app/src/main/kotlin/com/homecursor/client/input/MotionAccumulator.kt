package com.homecursor.client.input

/** Whole units to send, ready for the wire: both values fit an Int16. */
data class Motion(val dx: Int, val dy: Int) {
    val isZero: Boolean get() = dx == 0 && dy == 0
}

/**
 * Collects fractional motion and hands out whole units. The wire format carries integers, but a slow finger moves
 * the pointer by a fraction of a pixel per touch event; rounding each event would lose that motion, so the
 * remainder stays here and is added to the next [drain]. Motion beyond an Int16 stays too, so a long stall followed
 * by a drain does not lose distance.
 *
 * Meant for one thread (the one that feeds it and drains it).
 */
class MotionAccumulator {
    private var x = 0f
    private var y = 0f

    fun add(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    /** Takes the whole units collected so far, toward zero, at most Int16 per axis, and keeps the rest. */
    fun drain(): Motion {
        val dx = x.toInt().coerceIn(MIN_UNITS, MAX_UNITS)
        val dy = y.toInt().coerceIn(MIN_UNITS, MAX_UNITS)
        x -= dx
        y -= dy
        return Motion(dx, dy)
    }

    /** Forgets everything collected, for example when a gesture ends and the leftover should not leak into the next. */
    fun reset() {
        x = 0f
        y = 0f
    }

    private companion object {
        const val MIN_UNITS = Short.MIN_VALUE.toInt()
        const val MAX_UNITS = Short.MAX_VALUE.toInt()
    }
}
