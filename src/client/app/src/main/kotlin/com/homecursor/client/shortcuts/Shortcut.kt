package com.homecursor.client.shortcuts

import com.homecursor.client.input.InputSender
import com.homecursor.client.protocol.SpecialKey

/**
 * A key combination the user can send with one tap, such as Alt+Enter.
 *
 * @param id stable identity: the ids of [DefaultShortcuts] are fixed so they can be restored; custom ones are random.
 * @param keys pressed in order and released in reverse, so modifiers go first: `[Alt, Enter]`.
 */
data class Shortcut(val id: String, val name: String, val keys: List<SpecialKey>) {
    init {
        require(keys.isNotEmpty()) { "A shortcut needs at least one key." }
    }

    /** Presses every key in order, then releases them in reverse order. */
    fun send(sender: InputSender) {
        keys.forEach { sender.sendKey(it, true) }
        keys.asReversed().forEach { sender.sendKey(it, false) }
    }
}
