package com.homecursor.client.storage

import com.homecursor.client.shortcuts.Shortcut

/** Keeps the user's shortcut list, in order, on the phone. */
interface ShortcutStore {
    /** The saved list, or null if the user never changed it (the caller then starts from the defaults). */
    fun load(): List<Shortcut>?

    fun save(shortcuts: List<Shortcut>)
}
