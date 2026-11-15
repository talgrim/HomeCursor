package com.homecursor.client.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import com.homecursor.client.shortcuts.Shortcut
import com.homecursor.client.shortcuts.ShortcutSerializer

/**
 * Keeps the shortcut list in SharedPreferences as one serialized string. Use a preferences file of its own
 * ([FILE_NAME]): forgetting the PC clears the saved-host file, which must not delete the user's shortcuts.
 */
class SharedPrefsShortcutStore(private val preferences: SharedPreferences) : ShortcutStore {
    override fun load(): List<Shortcut>? = preferences.getString(KEY_SHORTCUTS, null)?.let(ShortcutSerializer::deserialize)

    override fun save(shortcuts: List<Shortcut>) = preferences.edit {
        putString(KEY_SHORTCUTS, ShortcutSerializer.serialize(shortcuts))
    }

    companion object {
        const val FILE_NAME = "shortcuts"
        private const val KEY_SHORTCUTS = "shortcuts"
    }
}
