package com.homecursor.client.storage

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Keeps the "tutorial seen" flag in SharedPreferences. Like [SharedPrefsTrackpadSettingsStore] it has a file of its
 * own ([FILE_NAME]), so forgetting the PC does not make the tour run again.
 */
class SharedPrefsTutorialStore(private val preferences: SharedPreferences) : TutorialStore {
    override fun isSeen() = preferences.getBoolean(KEY_SEEN, false)

    override fun markSeen() = preferences.edit { putBoolean(KEY_SEEN, true) }

    companion object {
        const val FILE_NAME = "tutorial"
        private const val KEY_SEEN = "seen"
    }
}
