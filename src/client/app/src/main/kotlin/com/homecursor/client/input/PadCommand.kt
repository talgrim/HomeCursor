package com.homecursor.client.input

import com.homecursor.client.protocol.MouseButton

/**
 * What the pad asks the host to do, as recognised by [TrackpadGestureInterpreter]. Distances are raw finger
 * travel in pixels, in screen coordinates (y grows downwards); sensitivity, acceleration and scroll direction
 * are applied later, so the interpreter does not depend on settings.
 */
sealed interface PadCommand {
    /**
     * The pointer moves by ([dx], [dy]) pixels of finger travel. [elapsedMillis] is the time that travel took,
     * which is what pointer acceleration needs to know the finger's speed.
     */
    data class Move(val dx: Float, val dy: Float, val elapsedMillis: Long) : PadCommand

    /** Two fingers moved by ([dx], [dy]) pixels, measured at their midpoint. */
    data class Scroll(val dx: Float, val dy: Float) : PadCommand

    /** A complete press and release of [button]. */
    data class Click(val button: MouseButton) : PadCommand

    /** Holds [button] down, for a drag. Always followed by [ButtonUp] when the gesture ends. */
    data class ButtonDown(val button: MouseButton) : PadCommand

    data class ButtonUp(val button: MouseButton) : PadCommand
}
