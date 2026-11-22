package com.homecursor.client.storage

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Keeps the update settings in SharedPreferences. Like the trackpad settings it has a file of its own
 * ([FILE_NAME]), so forgetting the PC does not reset it.
 */
class SharedPrefsUpdateSettingsStore(private val preferences: SharedPreferences) : UpdateSettingsStore {
    override var checksEnabled: Boolean
        get() = preferences.getBoolean(KEY_CHECKS_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_CHECKS_ENABLED, value) }

    override var lastCheckMillis: Long
        get() = preferences.getLong(KEY_LAST_CHECK, 0L)
        set(value) = preferences.edit { putLong(KEY_LAST_CHECK, value) }

    companion object {
        const val FILE_NAME = "updates"
        private const val KEY_CHECKS_ENABLED = "checks_enabled"
        private const val KEY_LAST_CHECK = "last_check"
    }
}
