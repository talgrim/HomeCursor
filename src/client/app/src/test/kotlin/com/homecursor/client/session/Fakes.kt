package com.homecursor.client.session

import com.homecursor.client.connection.ConnectionException
import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostConnector
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.connection.HostSession
import com.homecursor.client.connection.SessionState
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.discovery.HostDiscovery
import com.homecursor.client.storage.SavedHostStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class FakeSession(override val endpoint: HostEndpoint) : HostSession {
    override val sessionId = 1L
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Active)
    override val state: StateFlow<SessionState> = mutableState
    var closeCount = 0
        private set

    override fun close() {
        closeCount++
        mutableState.value = SessionState.Closed(null)
    }

    fun lose(failure: ConnectionFailure = ConnectionFailure.HostClosed) {
        mutableState.value = SessionState.Closed(failure)
    }
}

/** Decides each connect call by [behavior]: return a [ConnectionFailure] to throw it, or a [FakeSession]. */
class FakeConnector(var behavior: (endpoint: HostEndpoint, attempt: Int) -> Any) : HostConnector {
    val attempts = mutableListOf<HostEndpoint>()
    val sessions = mutableListOf<FakeSession>()

    override suspend fun connect(endpoint: HostEndpoint): HostSession {
        attempts += endpoint
        return when (val result = behavior(endpoint, attempts.size)) {
            is ConnectionFailure -> throw ConnectionException(result)
            is FakeSession -> result.also { sessions += it }
            else -> error("bad script")
        }
    }
}

class FakeDiscovery(var hosts: List<DiscoveredHost> = emptyList()) : HostDiscovery {
    var calls = 0
        private set

    override fun discover(): Flow<DiscoveredHost> = flow {
        calls++
        hosts.forEach { emit(it) }
    }
}

class FakeStore(var saved: HostEndpoint? = null) : SavedHostStore {
    override fun load() = saved
    override fun save(host: HostEndpoint) { saved = host }
    override fun clear() { saved = null }
}
