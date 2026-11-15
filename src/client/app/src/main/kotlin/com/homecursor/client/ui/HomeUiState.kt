package com.homecursor.client.ui

import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.session.ConnectionStatus

data class HomeUiState(
    val status: ConnectionStatus = ConnectionStatus.Idle,
    /** Hosts found by the last scan. */
    val hosts: List<DiscoveredHost> = emptyList(),
    val isScanning: Boolean = false,
)
