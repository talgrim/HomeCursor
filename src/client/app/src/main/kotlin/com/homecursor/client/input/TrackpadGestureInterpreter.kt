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
 * | One finger taps | left [PadCommand.Click], sent once the double-tap window has passed (see below) |
 * | Two fingers tap | right [PadCommand.Click] at once |
 * | Two fingers move | [PadCommand.Scroll] |
 * | Tap, then touch again and move | [PadCommand.ButtonDown] for the left button, moves, [PadCommand.ButtonUp] on release |
 * | Tap, then tap again quickly | two left clicks (a double click) |
 *
 * **Why a tap does not click at once.** If the click of the first tap were sent immediately, the button press that
 * starts a drag would be a second press at the same spot a moment later, which Windows treats as a double click (it
 * maximises a window whose title bar you tried to drag). So a tap waits: if the finger comes back within the
 * double-tap window and moves, the tap is absorbed and the host sees one press, a plain drag. Otherwise the click is
 * sent when the window ends: the caller must call [flushPendingClick] then (see [hasPendingClick]), or it is sent
 * with the next touch.
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
        val doubleTapWindowMillis: Long = 250L,
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

        /**
         * One finger is down. [followsTap] is set when it came down within the double-tap window of a tap: that tap's
         * click is then owed, and is either absorbed (this finger starts a drag), or sent when this finger turns
         * out to be a second tap, a long press, or a part of another gesture.
         */
        class OneFinger(val followsTap: Boolean, val downTime: Long, val start: Position) : State {
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

    /** When a tap was lifted whose left click has not been sent yet, or null. */
    private var pendingTapTime: Long? = null

    /**
     * True while a tap's left click is held back. The caller should wait the double-tap window from the moment this
     * turns true, and then call [flushPendingClick] unless a touch arrived meanwhile (which settles the click itself).
     */
    val hasPendingClick: Boolean get() = pendingTapTime != null

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
            State.Idle -> begin(timeMillis, pointers, commands)
            State.WaitForRelease -> if (pointers.isEmpty()) state = State.Idle
            is State.OneFinger -> continueOneFinger(current, timeMillis, pointers, commands)
            is State.TwoFingers -> continueTwoFingers(current, timeMillis, pointers, commands)
        }
        return commands
    }

    /** The double-tap window ended without a touch: sends the click of the tap that was waiting, if any. */
    fun flushPendingClick(): List<PadCommand> {
        if (pendingTapTime == null) return emptyList()
        pendingTapTime = null
        return listOf(PadCommand.Click(MouseButton.Left))
    }

    /**
     * The system took the touches away (or the screen is going away). Releases a held button and forgets the
     * gesture; no click is sent for it, not even for a tap that was waiting.
     */
    fun cancel(): List<PadCommand> {
        val commands = mutableListOf<PadCommand>()
        releaseHeldButton(commands)
        state = State.Idle
        pendingTapTime = null
        return commands
    }

    private fun begin(time: Long, pointers: List<TouchPoint>, out: MutableList<PadCommand>) {
        val tapTime = pendingTapTime
        val withinWindow = tapTime != null && time - tapTime <= config.doubleTapWindowMillis
        when (pointers.size) {
            0 -> {
                // A late frame (the caller's timer did not fire yet): the window is over, so the click is due.
                if (tapTime != null && !withinWindow) out += flushPendingClick()
                return
            }
            1 -> {
                if (tapTime != null && !withinWindow) out += flushPendingClick()
                state = State.OneFinger(followsTap = withinWindow, downTime = time, start = pointers[0].position())
            }
            else -> {
                // Two fingers right away: whatever tap was waiting is not going to be part of a drag.
                out += flushPendingClick()
                state = State.TwoFingers(time, centroid(pointers), canTap = true)
            }
        }
        // The waiting tap is now owed by the finger above (followsTap) or was just sent.
        pendingTapTime = null
    }

    private fun continueOneFinger(finger: State.OneFinger, time: Long, pointers: List<TouchPoint>, out: MutableList<PadCommand>) {
        when (pointers.size) {
            0 -> {
                if (finger.buttonHeld) {
                    out += PadCommand.ButtonUp(MouseButton.Left)
                } else if (!finger.moving) {
                    val isTap = time - finger.downTime <= config.tapTimeoutMillis
                    when {
                        // The second tap of a double click: both clicks now. A third tap starts over.
                        finger.followsTap && isTap -> out += listOf(leftClick, leftClick)
                        // Pressed and held after a tap, not moving: the tap's click was owed, the hold is nothing.
                        finger.followsTap -> out += leftClick
                        // A first tap: its click waits, to see whether the finger comes back to drag.
                        isTap -> pendingTapTime = time
                    }
                }
                state = State.Idle
            }
            1 -> moveOneFinger(finger, time, pointers[0].position(), out)
            else -> {
                releaseHeldButton(out)
                owedClick(finger, out)
                // Whatever the first finger did, it is no tap any more once a second one joined.
                state = State.TwoFingers(finger.downTime, centroid(pointers), canTap = !finger.moving)
            }
        }
    }

    private fun moveOneFinger(finger: State.OneFinger, time: Long, position: Position, out: MutableList<PadCommand>) {
        if (!finger.moving) {
            if (distance(position, finger.start) <= config.touchSlop) return
            finger.moving = true
            // The tap that came before is absorbed: the drag is a single press, not a second one.
            if (finger.followsTap) {
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
        val current = state
        releaseHeldButton(out)
        if (current is State.OneFinger) owedClick(current, out)
        // A tap that was still waiting was a real tap; the extra fingers do not undo it.
        out += flushPendingClick()
        state = State.WaitForRelease
    }

    /** The click of a tap that this finger followed and did not turn into a drag (it has not moved). */
    private fun owedClick(finger: State.OneFinger, out: MutableList<PadCommand>) {
        if (finger.followsTap && !finger.moving) out += leftClick
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

    private val leftClick = PadCommand.Click(MouseButton.Left)

    private companion object {
        const val MAX_FINGERS = 2
    }
}
