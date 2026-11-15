package com.homecursor.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecursor.client.input.InputSender
import com.homecursor.client.input.MotionAccumulator
import com.homecursor.client.input.PadCommand
import com.homecursor.client.input.PointerAcceleration
import com.homecursor.client.input.TouchPoint
import com.homecursor.client.input.TrackpadGestureInterpreter
import com.homecursor.client.input.TrackpadTuning
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.storage.TrackpadSettings
import com.homecursor.client.storage.TrackpadSettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Turns touches on the pad into pointer input for the host: the UI reports touches and the button bar, and this
 * runs them through [TrackpadGestureInterpreter], applies sensitivity, acceleration and scroll direction, and sends
 * the result to the current [InputSender].
 *
 * Move and scroll motion is collected and sent at most once per [TrackpadTuning.flushIntervalMillis], so a burst of
 * touch events becomes one datagram. Anything that must land in the right place (a click, a button press or
 * release) first sends the motion collected so far, so the pointer is where the user sees it when the click happens.
 *
 * Call everything from the main thread. Sending never blocks: the sender queues the input.
 *
 * @param inputSender the sender for the live connection (a no-op one while not connected), see
 *   `ConnectionCoordinator.inputSender`.
 */
class TrackpadViewModel(
    private val inputSender: StateFlow<InputSender>,
    private val settingsStore: TrackpadSettingsStore,
    private val tuning: TrackpadTuning = TrackpadTuning.forDensity(1f),
) : ViewModel() {
    private val interpreter = TrackpadGestureInterpreter(tuning.gestures)
    private val acceleration = PointerAcceleration(tuning.acceleration)
    private val pointerMotion = MotionAccumulator()
    private val scrollMotion = MotionAccumulator()

    /** Buttons held down by the button bar (not by a drag gesture), so they can be released when the app leaves. */
    private val heldBarButtons = mutableSetOf<MouseButton>()
    private var flushJob: Job? = null

    private val mutableSettings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<TrackpadSettings> = mutableSettings.asStateFlow()

    init {
        // A new connection (or none): whatever the old gesture had pending or held means nothing to it. A button
        // held on the old connection is released by the host itself when that connection ends.
        viewModelScope.launch { inputSender.collect { forgetGesture() } }
    }

    /** Reports every finger on the pad at [timeMillis] (a monotonic clock, in ms). An empty list means all lifted. */
    fun onTouch(timeMillis: Long, pointers: List<TouchPoint>) = handle(interpreter.onTouch(timeMillis, pointers))

    /** The system took the touches away. A held drag button is released, no click is sent. */
    fun onTouchCancelled() = handle(interpreter.cancel())

    /** A button of the button bar went down. */
    fun onButtonPressed(button: MouseButton) {
        if (!heldBarButtons.add(button)) return
        flush()
        inputSender.value.sendButton(button, true)
    }

    /** A button of the button bar went up. Ignored if it was not pressed (for example after [onBackground]). */
    fun onButtonReleased(button: MouseButton) {
        if (!heldBarButtons.remove(button)) return
        flush()
        inputSender.value.sendButton(button, false)
    }

    /** The app left the foreground: let go of everything held and drop motion that was not sent yet. */
    fun onBackground() {
        // Releases a drag button, if any. Done before the connection is closed, which the caller does next.
        handle(interpreter.cancel())
        heldBarButtons.toList().forEach { onButtonReleased(it) }
        forgetGesture()
    }

    fun onSensitivityChanged(sensitivity: Float) =
        update { TrackpadSettings.coerced(sensitivity, it.naturalScroll) }

    fun onNaturalScrollChanged(naturalScroll: Boolean) = update { it.copy(naturalScroll = naturalScroll) }

    private fun update(transform: (TrackpadSettings) -> TrackpadSettings) {
        val updated = transform(mutableSettings.value)
        mutableSettings.value = updated
        settingsStore.save(updated)
    }

    private fun handle(commands: List<PadCommand>) {
        for (command in commands) {
            when (command) {
                is PadCommand.Move -> {
                    val factor = acceleration.gain(command.dx, command.dy, command.elapsedMillis) * settings.value.sensitivity
                    pointerMotion.add(command.dx * factor, command.dy * factor)
                    scheduleFlush()
                }
                is PadCommand.Scroll -> {
                    scrollMotion.add(scrollUnits(command.dx, horizontal = true), scrollUnits(command.dy, horizontal = false))
                    scheduleFlush()
                }
                is PadCommand.Click -> {
                    flush()
                    inputSender.value.sendButton(command.button, true)
                    inputSender.value.sendButton(command.button, false)
                }
                is PadCommand.ButtonDown -> {
                    flush()
                    inputSender.value.sendButton(command.button, true)
                }
                is PadCommand.ButtonUp -> {
                    flush()
                    inputSender.value.sendButton(command.button, false)
                }
            }
        }
    }

    /**
     * Finger travel to wire scroll units. The wire counts up and right as positive, and screen y grows downwards.
     * Traditional scrolling moves the view the way a wheel does: fingers down scroll down, fingers right scroll right.
     * Natural scrolling moves the content with the fingers, so both flip.
     */
    private fun scrollUnits(travel: Float, horizontal: Boolean): Float {
        val natural = settings.value.naturalScroll
        val sign = if (horizontal != natural) 1f else -1f
        return sign * travel * tuning.scrollUnitsPerPixel
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
            delay(tuning.flushIntervalMillis)
            flush()
        }
    }

    private fun flush() {
        val sender = inputSender.value
        val move = pointerMotion.drain()
        if (!move.isZero) sender.sendMove(move.dx, move.dy)
        val scroll = scrollMotion.drain()
        if (!scroll.isZero) sender.sendScroll(scroll.dx, scroll.dy)
    }

    private fun forgetGesture() {
        flushJob?.cancel()
        flushJob = null
        interpreter.cancel()
        pointerMotion.reset()
        scrollMotion.reset()
        heldBarButtons.clear()
    }
}
