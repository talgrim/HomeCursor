package com.homecursor.client.input

import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.shortcuts.Shortcut
import com.homecursor.client.storage.ShortcutStore
import com.homecursor.client.storage.TrackpadSettings
import com.homecursor.client.storage.TrackpadSettingsStore

/** Records what it was asked to send, in order. */
class FakeInputSender : InputSender {
    sealed interface Event {
        data class Move(val dx: Int, val dy: Int) : Event

        data class Scroll(val horizontal: Int, val vertical: Int) : Event

        data class Button(val button: MouseButton, val isDown: Boolean) : Event

        data class Key(val key: SpecialKey, val isDown: Boolean) : Event

        data class Text(val text: String) : Event
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

    override fun sendKey(key: SpecialKey, isDown: Boolean) {
        events += Event.Key(key, isDown)
    }

    override fun sendText(text: String) {
        events += Event.Text(text)
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

class FakeShortcutStore(var saved: List<Shortcut>? = null) : ShortcutStore {
    var saveCount = 0
        private set

    override fun load() = saved

    override fun save(shortcuts: List<Shortcut>) {
        saved = shortcuts
        saveCount++
    }
}
