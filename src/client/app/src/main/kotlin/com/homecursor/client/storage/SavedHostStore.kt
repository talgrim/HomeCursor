package com.homecursor.client.storage

import com.homecursor.client.connection.HostEndpoint

/** Remembers the last host the app connected to. */
interface SavedHostStore {
    fun load(): HostEndpoint?

    fun save(host: HostEndpoint)

    fun clear()
}
