package com.homecursor.client.storage

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Keeps the trackpad settings in SharedPreferences. Use a preferences file of its own ([FILE_NAME]), not the one of
 * [SharedPrefsSavedHostStore]: that store clears its whole file when the user forgets the PC, which must not reset
 * the trackpad feel.
 */
class SharedPrefsTrackpadSettingsStore(private val preferences: SharedPreferences) : TrackpadSettingsStore {
    override fun load() = TrackpadSettings.coerced(
        sensitivity = preferences.getFloat(KEY_SENSITIVITY, TrackpadSettings.DEFAULT_SENSITIVITY),
        naturalScroll = preferences.getBoolean(KEY_NATURAL_SCROLL, false),
    )

    override fun save(settings: TrackpadSettings) = preferences.edit {
        putFloat(KEY_SENSITIVITY, settings.sensitivity)
        putBoolean(KEY_NATURAL_SCROLL, settings.naturalScroll)
    }

    companion object {
        const val FILE_NAME = "trackpad_settings"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_NATURAL_SCROLL = "natural_scroll"
    }
}
