package com.homecursor.client.storage

/** Whether the app looks for updates by itself and when it last did. */
interface UpdateSettingsStore {
    var checksEnabled: Boolean

    /** Epoch milliseconds of the last successful check, 0 if there was none. */
    var lastCheckMillis: Long
}
