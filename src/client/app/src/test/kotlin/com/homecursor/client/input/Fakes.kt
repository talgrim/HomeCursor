package com.homecursor.client.input

import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.storage.TrackpadSettings
import com.homecursor.client.storage.TrackpadSettingsStore

/** Records what it was asked to send, in order. */
class FakeInputSender : InputSender {
    sealed interface Event {
        data class Move(val dx: Int, val dy: Int) : Event

        data class Scroll(val horizontal: Int, val vertical: Int) : Event

        data class Button(val button: MouseButton, val isDown: Boolean) : Event
    }

    val events = mutableListOf<Event>()

    override fun sendMove(dx: Int, dy: Int) {
        events += Event.Move(dx, dy)
    }

    override fun sendScroll(horizontal: Int, vertical: Int) {
        events += Event.Scroll(horizontal, vertical)
    }

    override fun sendButton(button: MouseButton, isDown: Boolean) {
        events += Event.Button(button, isDown)
    }
}

class FakeTrackpadSettingsStore(var saved: TrackpadSettings = TrackpadSettings()) : TrackpadSettingsStore {
    var saveCount = 0
        private set

    override fun load() = saved

    override fun save(settings: TrackpadSettings) {
        saved = settings
        saveCount++
    }
}
