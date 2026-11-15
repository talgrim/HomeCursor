package com.homecursor.client.session

import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.connection.HostSession
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.input.FakeInputSender
import com.homecursor.client.input.InputSender
import com.homecursor.client.input.NoOpInputSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {
    private val saved = HostEndpoint("192.168.1.10", "Desktop", 47800, 47801)
    private val config = ConnectionCoordinator.Config(busyRetryIntervalMillis = 1_000, busyRetryAttempts = 5, reconnectDelayMillis = 500)

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    // Not backgroundScope: advanceUntilIdle() ignores tasks that belong to it.
    private fun TestScope.coordinator(
        connector: FakeConnector,
        discovery: FakeDiscovery = FakeDiscovery(),
        store: FakeStore = FakeStore(saved),
        inputSenders: (HostSession) -> InputSender = { NoOpInputSender },
    ): ConnectionCoordinator {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job()).also { scopes += it }
        return ConnectionCoordinator(connector, discovery, store, scope, dispatcher, config, inputSenders)
    }

    private fun hostAt(address: String, name: String = "Desktop") = DiscoveredHost(address, name, 47800, 47801, false)

    @Test
    fun start_noSavedHost_staysIdle() = runTest {
        val connector = FakeConnector { _, _ -> ConnectionFailure.Unreachable }
        val coordinator = coordinator(connector, store = FakeStore(null))

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Idle, coordinator.status.value)
        assertEquals(0, connector.attempts.size)
    }

    @Test
    fun start_returnsWhetherThereIsASavedHostToConnectTo() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }

        assertEquals(true, coordinator(connector).start())
        assertEquals(false, coordinator(connector, store = FakeStore(null)).start())
    }

    @Test
    fun start_savedHostReachable_connects() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val coordinator = coordinator(connector)

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected(saved), coordinator.status.value)
        assertEquals(listOf(saved), connector.attempts)
    }

    @Test
    fun inputSender_beforeConnecting_dropsInput() = runTest {
        val coordinator = coordinator(FakeConnector { endpoint, _ -> FakeSession(endpoint) })

        assertSame(NoOpInputSender, coordinator.inputSender.value)
    }

    @Test
    fun inputSender_connected_isTheSenderMadeForThatSession() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val made = mutableListOf<Pair<HostSession, FakeInputSender>>()
        val coordinator = coordinator(connector) { session -> FakeInputSender().also { made += session to it } }

        coordinator.start()
        advanceUntilIdle()

        assertEquals(1, made.size)
        assertSame(connector.sessions.single(), made.single().first)
        assertSame(made.single().second, coordinator.inputSender.value)
    }

    @Test
    fun inputSender_isAlreadySetWhenTheStatusTurnsConnected() = runTest {
        val sender = FakeInputSender()
        val coordinator = coordinator(FakeConnector { endpoint, _ -> FakeSession(endpoint) }) { sender }
        var seenWhenConnected: InputSender? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.status.collect { if (it is ConnectionStatus.Connected) seenWhenConnected = coordinator.inputSender.value }
        }

        coordinator.start()
        advanceUntilIdle()

        assertSame(sender, seenWhenConnected)
    }

    @Test
    fun inputSender_connectionLost_dropsInputUntilReconnectedWithANewSender() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val made = mutableListOf<FakeInputSender>()
        val coordinator = coordinator(connector) { FakeInputSender().also { made += it } }
        coordinator.start()
        advanceUntilIdle()

        connector.sessions.single().lose()
        runCurrent()
        assertSame(NoOpInputSender, coordinator.inputSender.value)

        advanceUntilIdle()
        assertEquals(2, made.size)
        assertSame(made[1], coordinator.inputSender.value)
    }

    @Test
    fun inputSender_stop_dropsInput() = runTest {
        val coordinator = coordinator(FakeConnector { endpoint, _ -> FakeSession(endpoint) }) { FakeInputSender() }
        coordinator.start()
        advanceUntilIdle()

        coordinator.stop()

        assertSame(NoOpInputSender, coordinator.inputSender.value)
    }

    @Test
    fun inputSender_connectFails_staysNoOp() = runTest {
        val coordinator = coordinator(FakeConnector { _, _ -> ConnectionFailure.Unreachable }) { FakeInputSender() }

        coordinator.start()
        advanceUntilIdle()

        assertSame(NoOpInputSender, coordinator.inputSender.value)
    }

    @Test
    fun start_hostMovedToNewAddress_findsItByNameAndRemembersNewAddress() = runTest {
        val connector = FakeConnector { endpoint, _ ->
            if (endpoint.address == "192.168.1.99") FakeSession(endpoint) else ConnectionFailure.Timeout
        }
        val discovery = FakeDiscovery(listOf(hostAt("192.168.1.50", "Laptop"), hostAt("192.168.1.99")))
        val store = FakeStore(saved)
        val coordinator = coordinator(connector, discovery, store)

        coordinator.start()
        advanceUntilIdle()

        val moved = HostEndpoint("192.168.1.99", "Desktop", 47800, 47801)
        assertEquals(ConnectionStatus.Connected(moved), coordinator.status.value)
        assertEquals(moved, store.saved)
    }

    @Test
    fun start_hostNowhereOnNetwork_failsWithOriginalFailure() = runTest {
        val connector = FakeConnector { _, _ -> ConnectionFailure.Unreachable }
        val coordinator = coordinator(connector, FakeDiscovery(listOf(hostAt("10.0.0.5", "Other"))))

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Failed(saved, ConnectionFailure.Unreachable), coordinator.status.value)
    }

    @Test
    fun start_hostBusyThenFree_retriesUntilConnected() = runTest {
        val connector = FakeConnector { endpoint, attempt ->
            if (attempt <= 3) ConnectionFailure.Busy else FakeSession(endpoint)
        }
        val coordinator = coordinator(connector)

        coordinator.start()
        runCurrent()
        assertEquals(ConnectionStatus.Busy(saved), coordinator.status.value)

        advanceUntilIdle()
        assertEquals(ConnectionStatus.Connected(saved), coordinator.status.value)
        assertEquals(4, connector.attempts.size)
    }

    @Test
    fun start_hostStaysBusy_givesUpAfterConfiguredAttemptsWithoutSearching() = runTest {
        val connector = FakeConnector { _, _ -> ConnectionFailure.Busy }
        val discovery = FakeDiscovery(listOf(hostAt("192.168.1.99")))
        val coordinator = coordinator(connector, discovery)

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Failed(saved, ConnectionFailure.Busy), coordinator.status.value)
        assertEquals(5, connector.attempts.size)
        assertEquals(0, discovery.calls)
    }

    @Test
    fun start_unsupportedVersion_failsAtOnceWithoutSearching() = runTest {
        val connector = FakeConnector { _, _ -> ConnectionFailure.UnsupportedVersion }
        val discovery = FakeDiscovery(listOf(hostAt("192.168.1.99")))
        val coordinator = coordinator(connector, discovery)

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Failed(saved, ConnectionFailure.UnsupportedVersion), coordinator.status.value)
        assertEquals(1, connector.attempts.size)
        assertEquals(0, discovery.calls)
    }

    @Test
    fun connectTo_hostBusy_triesOnlyOnce() = runTest {
        val connector = FakeConnector { _, _ -> ConnectionFailure.Busy }
        val coordinator = coordinator(connector)

        coordinator.connectTo(saved)
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Failed(saved, ConnectionFailure.Busy), coordinator.status.value)
        assertEquals(1, connector.attempts.size)
    }

    @Test
    fun connectTo_success_remembersHost() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val store = FakeStore(null)
        val coordinator = coordinator(connector, store = store)

        coordinator.connectTo(saved)
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected(saved), coordinator.status.value)
        assertEquals(saved, store.saved)
    }

    @Test
    fun connectionLost_reconnectsAfterDelay() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val coordinator = coordinator(connector)
        coordinator.start()
        advanceUntilIdle()

        connector.sessions.single().lose()
        runCurrent()
        assertEquals(ConnectionStatus.Connecting(saved, isReconnect = true), coordinator.status.value)
        assertEquals(1, connector.attempts.size)

        advanceTimeBy(500)
        advanceUntilIdle()
        assertEquals(ConnectionStatus.Connected(saved), coordinator.status.value)
        assertEquals(2, connector.attempts.size)
    }

    @Test
    fun stop_closesSessionAndGoesIdle() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val coordinator = coordinator(connector)
        coordinator.start()
        advanceUntilIdle()

        coordinator.stop()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Idle, coordinator.status.value)
        assertEquals(1, connector.sessions.single().closeCount)
        assertEquals(1, connector.attempts.size)
    }

    @Test
    fun stop_whileWaitingForBusyHost_stopsRetrying() = runTest {
        val connector = FakeConnector { _, _ -> ConnectionFailure.Busy }
        val coordinator = coordinator(connector)
        coordinator.start()
        runCurrent()

        coordinator.stop()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Idle, coordinator.status.value)
        assertEquals(1, connector.attempts.size)
    }

    @Test
    fun startAfterStop_connectsAgain() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val coordinator = coordinator(connector)
        coordinator.start()
        advanceUntilIdle()
        coordinator.stop()
        advanceUntilIdle()

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected(saved), coordinator.status.value)
        assertEquals(2, connector.sessions.size)
    }

    @Test
    fun start_whileAlreadyConnected_doesNothing() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val coordinator = coordinator(connector)
        coordinator.start()
        advanceUntilIdle()

        coordinator.start()
        advanceUntilIdle()

        assertEquals(1, connector.attempts.size)
    }

    @Test
    fun forget_disconnectsAndClearsSavedHost() = runTest {
        val connector = FakeConnector { endpoint, _ -> FakeSession(endpoint) }
        val store = FakeStore(saved)
        val coordinator = coordinator(connector, store = store)
        coordinator.start()
        advanceUntilIdle()

        coordinator.forget()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Idle, coordinator.status.value)
        assertNull(store.saved)
        assertEquals(1, connector.sessions.single().closeCount)
    }
}
