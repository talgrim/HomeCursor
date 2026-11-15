package com.homecursor.client.storage

/** Remembers the trackpad settings between app starts. */
interface TrackpadSettingsStore {
    /** The stored settings, or the defaults if nothing was stored yet. Never throws on bad stored values. */
    fun load(): TrackpadSettings

    fun save(settings: TrackpadSettings)
}
