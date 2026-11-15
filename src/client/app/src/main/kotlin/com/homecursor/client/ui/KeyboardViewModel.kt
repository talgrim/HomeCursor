package com.homecursor.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecursor.client.input.InputSender
import com.homecursor.client.protocol.SpecialKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How the on-screen keyboard sends what is typed. */
enum class KeyboardMode {
    /** Every key goes to the host at once, as a key press. Supports shortcuts and held modifiers. */
    RealTime,

    /** Text is typed into a field on the phone (any language, with the phone's own keyboard) and sent as a whole. */
    Compose,
}

/**
 * State and input of the full-screen on-screen keyboard (shown in landscape). Modifier keys are sticky: a tap holds
 * the modifier on the host until the next ordinary key has been pressed (or the modifier is tapped again), which is
 * how Ctrl+C is typed on a touch screen.
 *
 * Call from the main thread. Survives rotation, which is what makes the keyboard stay open while the phone turns.
 */
class KeyboardViewModel(private val inputSender: StateFlow<InputSender>) : ViewModel() {
    private val mutableIsOpen = MutableStateFlow(false)

    /** Whether the user wants the keyboard. The screen turns landscape while it is true. */
    val isOpen: StateFlow<Boolean> = mutableIsOpen.asStateFlow()

    private val mutableMode = MutableStateFlow(KeyboardMode.RealTime)
    val mode: StateFlow<KeyboardMode> = mutableMode.asStateFlow()

    private val mutableHeldModifiers = MutableStateFlow<Set<SpecialKey>>(emptySet())

    /** The modifiers currently held down on the host, for highlighting. */
    val heldModifiers: StateFlow<Set<SpecialKey>> = mutableHeldModifiers.asStateFlow()

    init {
        // A new connection (or none): the host released what the old connection held when it ended.
        viewModelScope.launch { inputSender.collect { mutableHeldModifiers.value = emptySet() } }
    }

    fun open() {
        mutableIsOpen.value = true
    }

    fun close() {
        releaseModifiers()
        mutableIsOpen.value = false
    }

    fun onModeChanged(mode: KeyboardMode) {
        releaseModifiers()
        mutableMode.value = mode
    }

    /** A key of the keyboard was tapped. A modifier is latched or unlatched, any other key is typed. */
    fun onKeyTapped(key: SpecialKey) {
        if (key.isModifier) {
            toggleModifier(key)
            return
        }
        val sender = inputSender.value
        sender.sendKey(key, true)
        sender.sendKey(key, false)
        releaseModifiers()
    }

    /** Types [text] on the host (compose mode). Held modifiers do not apply to it, so they are released first. */
    fun onTextSent(text: String) {
        if (text.isEmpty()) return
        releaseModifiers()
        inputSender.value.sendText(text)
    }

    /** The app left the foreground: let go of held modifiers, before the connection is closed, and close the keyboard. */
    fun onBackground() = close()

    private fun toggleModifier(key: SpecialKey) {
        val held = mutableHeldModifiers.value
        inputSender.value.sendKey(key, key !in held)
        mutableHeldModifiers.value = if (key in held) held - key else held + key
    }

    private fun releaseModifiers() {
        val held = mutableHeldModifiers.value
        if (held.isEmpty()) return
        held.forEach { inputSender.value.sendKey(it, false) }
        mutableHeldModifiers.value = emptySet()
    }
}
