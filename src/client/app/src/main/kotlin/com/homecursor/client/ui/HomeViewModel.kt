package com.homecursor.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.discovery.HostDiscovery
import com.homecursor.client.session.ConnectionCoordinator
import com.homecursor.client.session.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state and actions. The connection lives as long as this ViewModel (so it survives rotation) but
 * only while the app is in the foreground: the Activity calls [onForeground] and [onBackground].
 */
class HomeViewModel(
    private val discovery: HostDiscovery,
    coordinatorFactory: (CoroutineScope) -> ConnectionCoordinator,
) : ViewModel() {
    private val coordinator = coordinatorFactory(viewModelScope)

    private class Scan(val hosts: List<DiscoveredHost> = emptyList(), val isScanning: Boolean = false)

    private val scan = MutableStateFlow(Scan())
    private var scanJob: Job? = null
    private var isForeground = false

    val uiState: StateFlow<HomeUiState> =
        combine(coordinator.status, scan) { status, scan -> HomeUiState(status, scan.hosts, scan.isScanning) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            coordinator.status.collect { status ->
                when (status) {
                    // Nothing is connected or being connected: show who is out there.
                    is ConnectionStatus.Idle, is ConnectionStatus.Failed -> if (isForeground) startScan()
                    else -> stopScan()
                }
            }
        }
    }

    fun onForeground() {
        isForeground = true
        val connecting = coordinator.start()
        if (!connecting && scanJob?.isActive != true) startScan()
    }

    fun onBackground() {
        isForeground = false
        stopScan()
        coordinator.stop()
    }

    fun rescan() = startScan()

    fun connectTo(host: DiscoveredHost) =
        coordinator.connectTo(HostEndpoint(host.address, host.name, host.tcpPort, host.udpPort))

    /** Returns false if [input] is not a valid address. */
    fun connectManually(input: String): Boolean {
        val endpoint = parseManualEndpoint(input) ?: return false
        coordinator.connectTo(endpoint)
        return true
    }

    fun cancelConnecting() = coordinator.stop()

    fun disconnect() = coordinator.stop()

    fun forgetHost() = coordinator.forget()

    private fun startScan() {
        scanJob?.cancel()
        scan.value = Scan(isScanning = true)
        scanJob = viewModelScope.launch {
            try {
                discovery.discover().collect { host ->
                    scan.update { Scan(it.hosts + host, isScanning = true) }
                }
            } finally {
                scan.update { Scan(it.hosts, isScanning = false) }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scan.update { Scan(it.hosts, isScanning = false) }
    }
}
