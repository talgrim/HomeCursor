package com.homecursor.client.ui

import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.session.ConnectionCoordinator
import com.homecursor.client.session.ConnectionStatus
import com.homecursor.client.session.FakeConnector
import com.homecursor.client.session.FakeDiscovery
import com.homecursor.client.session.FakeSession
import com.homecursor.client.session.FakeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val saved = HostEndpoint("192.168.1.10", "Desktop", 47800, 47801)
    private val found = DiscoveredHost("192.168.1.10", "Desktop", 47800, 47801, false)
    private val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        connector: FakeConnector,
        discovery: FakeDiscovery,
        store: FakeStore,
    ) = HomeViewModel(discovery) { scope ->
        ConnectionCoordinator(connector, discovery, store, scope, StandardTestDispatcher(scheduler))
    }

    private fun reachable() = FakeConnector { endpoint, _ -> FakeSession(endpoint) }

    @Test
    fun onForeground_savedHost_connectsWithoutScanning() = runTest(scheduler) {
        val discovery = FakeDiscovery(listOf(found))
        val vm = viewModel(reachable(), discovery, FakeStore(saved))

        vm.onForeground()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected(saved), vm.uiState.value.status)
        assertEquals(0, discovery.calls)
    }

    @Test
    fun onForeground_noSavedHost_listsDiscoveredHosts() = runTest(scheduler) {
        val vm = viewModel(reachable(), FakeDiscovery(listOf(found)), FakeStore(null))

        vm.onForeground()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ConnectionStatus.Idle, state.status)
        assertEquals(listOf(found), state.hosts)
        assertFalse(state.isScanning)
    }

    @Test
    fun connectTo_pickedHost_connectsAndRemembersIt() = runTest(scheduler) {
        val store = FakeStore(null)
        val vm = viewModel(reachable(), FakeDiscovery(listOf(found)), store)
        vm.onForeground()
        advanceUntilIdle()

        vm.connectTo(found)
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected(saved), vm.uiState.value.status)
        assertEquals(saved, store.saved)
    }

    @Test
    fun connectFails_scansAgainSoTheUserCanPickAnotherHost() = runTest(scheduler) {
        val connector = FakeConnector { _, _ -> ConnectionFailure.Busy }
        val discovery = FakeDiscovery(listOf(found))
        val vm = viewModel(connector, discovery, FakeStore(null))
        vm.onForeground()
        advanceUntilIdle()

        vm.connectTo(found)
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Failed(saved, ConnectionFailure.Busy), vm.uiState.value.status)
        assertEquals(2, discovery.calls)
        assertEquals(listOf(found), vm.uiState.value.hosts)
    }

    @Test
    fun onBackground_closesConnectionAndDoesNotScan() = runTest(scheduler) {
        val connector = reachable()
        val discovery = FakeDiscovery(listOf(found))
        val vm = viewModel(connector, discovery, FakeStore(saved))
        vm.onForeground()
        advanceUntilIdle()

        vm.onBackground()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Idle, vm.uiState.value.status)
        assertEquals(1, connector.sessions.single().closeCount)
        assertEquals(0, discovery.calls)
    }

    @Test
    fun onForegroundAgain_reconnects() = runTest(scheduler) {
        val connector = reachable()
        val vm = viewModel(connector, FakeDiscovery(), FakeStore(saved))
        vm.onForeground()
        advanceUntilIdle()
        vm.onBackground()
        advanceUntilIdle()

        vm.onForeground()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected(saved), vm.uiState.value.status)
        assertEquals(2, connector.sessions.size)
    }

    @Test
    fun onForeground_whileConnected_keepsTheConnection() = runTest(scheduler) {
        // What happens after a rotation: the new Activity calls onForeground again.
        val connector = reachable()
        val vm = viewModel(connector, FakeDiscovery(), FakeStore(saved))
        vm.onForeground()
        advanceUntilIdle()

        vm.onForeground()
        advanceUntilIdle()

        assertEquals(1, connector.sessions.size)
        assertEquals(0, connector.sessions.single().closeCount)
    }

    @Test
    fun connectManually_invalidInput_returnsFalseAndDoesNotConnect() = runTest(scheduler) {
        val connector = reachable()
        val vm = viewModel(connector, FakeDiscovery(), FakeStore(null))

        assertFalse(vm.connectManually("not an address"))
        advanceUntilIdle()

        assertEquals(0, connector.attempts.size)
    }

    @Test
    fun connectManually_validInput_connectsToThatAddress() = runTest(scheduler) {
        val connector = reachable()
        val vm = viewModel(connector, FakeDiscovery(), FakeStore(null))

        assertTrue(vm.connectManually("10.0.2.2:50000"))
        advanceUntilIdle()

        assertEquals(HostEndpoint("10.0.2.2", "10.0.2.2", 50000), connector.attempts.single())
    }

    @Test
    fun forgetHost_disconnectsAndClearsSavedHost() = runTest(scheduler) {
        val store = FakeStore(saved)
        val vm = viewModel(reachable(), FakeDiscovery(), store)
        vm.onForeground()
        advanceUntilIdle()

        vm.forgetHost()
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Idle, vm.uiState.value.status)
        assertEquals(null, store.saved)
    }
}
