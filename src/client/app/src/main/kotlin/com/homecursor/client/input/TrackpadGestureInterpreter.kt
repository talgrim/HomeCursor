package com.homecursor.client.input

import com.homecursor.client.protocol.MouseButton
import kotlin.math.hypot

/**
 * Turns touch frames into [PadCommand]s. It knows nothing about Android or Compose: the UI reports, on every touch
 * event, which fingers are down and where, and the interpreter works out the gesture from how that set changes.
 *
 * | Gesture | Result |
 * |---|---|
 * | One finger moves | [PadCommand.Move] |
 * | One finger taps | left [PadCommand.Click] |
 * | Two fingers tap | right [PadCommand.Click] |
 * | Two fingers move | [PadCommand.Scroll] |
 * | Tap, then touch again and move | [PadCommand.ButtonDown] for the left button, moves, [PadCommand.ButtonUp] on release |
 * | Tap, then tap again quickly | a second left click (a double click) |
 *
 * Rules that keep gestures from bleeding into each other:
 * - Nothing moves until the finger travelled more than the touch slop, so a tap never nudges the pointer. The first
 *   move then includes the distance already travelled, so the pointer does not lag behind the finger.
 * - A click needs the finger to stay still and be lifted within the tap timeout.
 * - Once two fingers were down, lifting one never turns the other into a pointer: nothing is sent until all fingers
 *   are lifted.
 * - A third finger ends the gesture the same way (it is most likely a palm). A held button is released first.
 *
 * Not thread-safe: call it from one thread (the UI thread). Times come from the caller (milliseconds, monotonic),
 * so tests do not need a clock.
 */
class TrackpadGestureInterpreter(private val config: Config = Config()) {
    /** Distances are in pixels, so the UI should scale [touchSlop] by the screen density. */
    data class Config(
        val touchSlop: Float = 10f,
        val tapTimeoutMillis: Long = 250L,
        val doubleTapWindowMillis: Long = 300L,
    ) {
        init {
            require(touchSlop >= 0f) { "touchSlop must not be negative." }
            require(tapTimeoutMillis >= 0 && doubleTapWindowMillis >= 0) { "Timeouts must not be negative." }
        }
    }

    private sealed interface State {
        data object Idle : State

        /** The gesture ended early (third finger); ignore touches until every finger is lifted. */
        data object WaitForRelease : State

        /** One finger is down. [startsDrag] is set when it came down shortly after a tap. */
        class OneFinger(val startsDrag: Boolean, val downTime: Long, val start: Position) : State {
            var last = start
            var lastTime = downTime
            var moving = false
            var buttonHeld = false
        }

        /** Two fingers are down. They may still turn out to be a tap ([canTap]) or a scroll ([scrolling]). */
        class TwoFingers(val firstDownTime: Long, val start: Position, var canTap: Boolean) : State {
            var last = start
            var scrolling = false
        }
    }

    private data class Position(val x: Float, val y: Float)

    private var state: State = State.Idle

    /** When the last tap was lifted, or null if the last gesture was not a tap that could start a drag. */
    private var lastTapTime: Long? = null

    /**
     * Reports the fingers on the pad at [timeMillis]: every finger that is down, including ones that did not move.
     * An empty list means all fingers were lifted. Returns the commands the gesture produced, possibly none.
     */
    fun onTouch(timeMillis: Long, pointers: List<TouchPoint>): List<PadCommand> {
        val commands = mutableListOf<PadCommand>()
        if (pointers.size > MAX_FINGERS) {
            endGesture(commands)
            return commands
        }
        when (val current = state) {
            State.Idle -> begin(timeMillis, pointers)
            State.WaitForRelease -> if (pointers.isEmpty()) state = State.Idle
            is State.OneFinger -> continueOneFinger(current, timeMillis, pointers, commands)
            is State.TwoFingers -> continueTwoFingers(current, timeMillis, pointers, commands)
        }
        return commands
    }

    /**
     * The system took the touches away (or the screen is going away). Releases a held button and forgets the
     * gesture; no click is sent for it.
     */
    fun cancel(): List<PadCommand> {
        val commands = mutableListOf<PadCommand>()
        releaseHeldButton(commands)
        state = State.Idle
        lastTapTime = null
        return commands
    }

    private fun begin(time: Long, pointers: List<TouchPoint>) {
        when (pointers.size) {
            0 -> Unit
            1 -> {
                val startsDrag = lastTapTime?.let { time - it <= config.doubleTapWindowMillis } == true
                state = State.OneFinger(startsDrag, time, pointers[0].position())
            }
            else -> state = State.TwoFingers(time, centroid(pointers), canTap = true)
        }
        if (pointers.isNotEmpty()) lastTapTime = null
    }

    private fun continueOneFinger(finger: State.OneFinger, time: Long, pointers: List<TouchPoint>, out: MutableList<PadCommand>) {
        when (pointers.size) {
            0 -> {
                if (finger.buttonHeld) {
                    out += PadCommand.ButtonUp(MouseButton.Left)
                } else if (!finger.moving && time - finger.downTime <= config.tapTimeoutMillis) {
                    out += PadCommand.Click(MouseButton.Left)
                    // A tap that was itself the second one does not chain into a third.
                    lastTapTime = if (finger.startsDrag) null else time
                }
                state = State.Idle
            }
            1 -> moveOneFinger(finger, time, pointers[0].position(), out)
            else -> {
                releaseHeldButton(out)
                // Whatever the first finger did, it is no tap any more once a second one joined.
                state = State.TwoFingers(finger.downTime, centroid(pointers), canTap = !finger.moving)
            }
        }
    }

    private fun moveOneFinger(finger: State.OneFinger, time: Long, position: Position, out: MutableList<PadCommand>) {
        if (!finger.moving) {
            if (distance(position, finger.start) <= config.touchSlop) return
            finger.moving = true
            if (finger.startsDrag) {
                finger.buttonHeld = true
                out += PadCommand.ButtonDown(MouseButton.Left)
            }
        }
        val dx = position.x - finger.last.x
        val dy = position.y - finger.last.y
        // lastTime stays at the touch-down until the first move, so the first (slop-sized) jump is timed over the
        // whole time it took and does not read as a sudden fast flick.
        if (dx != 0f || dy != 0f) out += PadCommand.Move(dx, dy, time - finger.lastTime)
        finger.last = position
        finger.lastTime = time
    }

    private fun continueTwoFingers(fingers: State.TwoFingers, time: Long, pointers: List<TouchPoint>, out: MutableList<PadCommand>) {
        if (pointers.size == 2) {
            val middle = centroid(pointers)
            if (!fingers.scrolling) {
                if (distance(middle, fingers.start) <= config.touchSlop) return
                fingers.scrolling = true
            }
            val dx = middle.x - fingers.last.x
            val dy = middle.y - fingers.last.y
            if (dx != 0f || dy != 0f) out += PadCommand.Scroll(dx, dy)
            fingers.last = middle
            return
        }
        // A finger was lifted. Decide at once whether this was a tap, so the click does not wait for the other finger.
        if (fingers.canTap && !fingers.scrolling && time - fingers.firstDownTime <= config.tapTimeoutMillis) {
            out += PadCommand.Click(MouseButton.Right)
        }
        state = if (pointers.isEmpty()) State.Idle else State.WaitForRelease
    }

    private fun endGesture(out: MutableList<PadCommand>) {
        releaseHeldButton(out)
        state = State.WaitForRelease
        lastTapTime = null
    }

    private fun releaseHeldButton(out: MutableList<PadCommand>) {
        val current = state
        if (current is State.OneFinger && current.buttonHeld) {
            current.buttonHeld = false
            out += PadCommand.ButtonUp(MouseButton.Left)
        }
    }

    private fun TouchPoint.position() = Position(x, y)

    private fun centroid(pointers: List<TouchPoint>) =
        Position(pointers.map { it.x }.average().toFloat(), pointers.map { it.y }.average().toFloat())

    private fun distance(a: Position, b: Position) = hypot(a.x - b.x, a.y - b.y)

    private companion object {
        const val MAX_FINGERS = 2
    }
}
