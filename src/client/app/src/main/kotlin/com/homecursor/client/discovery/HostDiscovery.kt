package com.homecursor.client.discovery

import kotlinx.coroutines.flow.Flow

/** Finds HomeCursor hosts on the local network. */
interface HostDiscovery {
    /**
     * Probes the network and emits each host once, as soon as it answers. The flow completes when the
     * search window is over; cancel the collector to stop earlier.
     */
    fun discover(): Flow<DiscoveredHost>
}
