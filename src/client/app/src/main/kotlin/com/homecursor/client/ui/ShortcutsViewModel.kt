package com.homecursor.client.ui

import androidx.lifecycle.ViewModel
import com.homecursor.client.input.InputSender
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.shortcuts.DefaultShortcuts
import com.homecursor.client.shortcuts.Shortcut
import com.homecursor.client.storage.ShortcutStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's list of shortcuts: running one on the host, and adding, editing, deleting, reordering and restoring
 * the defaults. Every change is saved at once. Call from the main thread.
 *
 * A fresh install (nothing saved yet) starts with [DefaultShortcuts.all].
 */
class ShortcutsViewModel(
    private val inputSender: StateFlow<InputSender>,
    private val store: ShortcutStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableShortcuts = MutableStateFlow(store.load() ?: DefaultShortcuts.all)
    val shortcuts: StateFlow<List<Shortcut>> = mutableShortcuts.asStateFlow()

    private val mutableIsOpen = MutableStateFlow(false)

    /**
     * Whether the shortcuts screen is the one shown. Kept here, not in a composable, so that it outlives the
     * connected screen: locking the phone drops the connection and brings the screen back after the reconnect.
     */
    val isOpen: StateFlow<Boolean> = mutableIsOpen.asStateFlow()

    fun open() {
        mutableIsOpen.value = true
    }

    fun close() {
        mutableIsOpen.value = false
    }

    /** Sends the shortcut's keys to the host: all down in order, then all up in reverse. */
    fun run(shortcut: Shortcut) = shortcut.send(inputSender.value)

    fun add(name: String, keys: List<SpecialKey>) {
        if (keys.isEmpty()) return
        change { it + Shortcut(newId(), name.trim(), modifiersFirst(keys)) }
    }

    fun update(id: String, name: String, keys: List<SpecialKey>) {
        if (keys.isEmpty()) return
        change { list -> list.map { if (it.id == id) it.copy(name = name.trim(), keys = modifiersFirst(keys)) else it } }
    }

    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    /** Moves a shortcut [offset] places up (negative) or down (positive); stops at the ends of the list. */
    fun move(id: String, offset: Int) = change { list ->
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@change list
        val to = (from + offset).coerceIn(0, list.lastIndex)
        list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Adds the default shortcuts that are missing (deleted ones) at the end; the others stay as the user left them. */
    fun restoreDefaults() = change { list ->
        list + DefaultShortcuts.all.filter { default -> list.none { it.id == default.id } }
    }

    /** A modifier has to be down before the key it modifies, whatever order the user picked the keys in. */
    private fun modifiersFirst(keys: List<SpecialKey>) = keys.distinct().sortedBy { !it.isModifier }

    private fun change(transform: (List<Shortcut>) -> List<Shortcut>) {
        val updated = transform(mutableShortcuts.value)
        if (updated == mutableShortcuts.value) return
        mutableShortcuts.value = updated
        store.save(updated)
    }
}
