package com.homecursor.client.input

import com.homecursor.client.protocol.MouseButton

/**
 * Sends pointer input to the host. Every method returns at once and never throws: delivery happens in the
 * background, and a dead connection is reported through the session state, not here.
 */
interface InputSender {
    /** Moves the pointer by a relative number of pixels. Best effort: may be lost, never delayed behind a retry. */
    fun sendMove(dx: Int, dy: Int)

    /** Scrolls in 1/120 of a wheel notch. Positive is up ([vertical]) or right ([horizontal]). Best effort. */
    fun sendScroll(horizontal: Int, vertical: Int)

    /** Presses or releases a mouse button. Reliable: a lost release would leave the button stuck down. */
    fun sendButton(button: MouseButton, isDown: Boolean)
}

/** Used while there is no connection: input is dropped. */
object NoOpInputSender : InputSender {
    override fun sendMove(dx: Int, dy: Int) = Unit

    override fun sendScroll(horizontal: Int, vertical: Int) = Unit

    override fun sendButton(button: MouseButton, isDown: Boolean) = Unit
}
