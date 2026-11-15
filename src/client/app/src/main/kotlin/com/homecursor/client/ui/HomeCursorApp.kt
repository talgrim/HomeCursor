package com.homecursor.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homecursor.client.R
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.session.ConnectionStatus
import com.homecursor.client.ui.theme.HomeCursorTheme
import com.homecursor.client.ui.theme.Spacing

/** Root composable: shows the connection progress, the trackpad, or the list of hosts to pick from. */
@Composable
fun HomeCursorApp(
    viewModel: HomeViewModel,
    trackpadViewModel: TrackpadViewModel,
    keyboardViewModel: KeyboardViewModel,
    shortcutsViewModel: ShortcutsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeCursorContent(
        state = state,
        onHostClick = viewModel::connectTo,
        onRescan = viewModel::rescan,
        onManualConnect = viewModel::connectManually,
        onCancel = viewModel::cancelConnecting,
        connectedContent = { host ->
            ConnectedScreen(
                host = host,
                trackpadViewModel = trackpadViewModel,
                keyboardViewModel = keyboardViewModel,
                shortcutsViewModel = shortcutsViewModel,
                onDisconnect = viewModel::disconnect,
                onForget = viewModel::forgetHost,
            )
        },
    )
}

/** Stateless root. [connectedContent] is what is shown while connected, so this does not depend on the trackpad. */
@Composable
fun HomeCursorContent(
    state: HomeUiState,
    onHostClick: (DiscoveredHost) -> Unit,
    onRescan: () -> Unit,
    onManualConnect: (String) -> Boolean,
    onCancel: () -> Unit,
    connectedContent: @Composable (HostEndpoint) -> Unit,
) {
    HomeCursorTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // The trackpad gets almost the whole screen: touches outside it would be lost.
            val margin = if (state.status is ConnectionStatus.Connected) Spacing.sm else Spacing.lg
            Box(modifier = Modifier.safeDrawingPadding().padding(margin)) {
                when (val status = state.status) {
                    is ConnectionStatus.Connected -> connectedContent(status.host)
                    is ConnectionStatus.Connecting -> ProgressScreen(
                        stringResource(
                            if (status.isReconnect) R.string.reconnecting_to else R.string.connecting_to,
                            status.host.name,
                        ),
                        onCancel,
                    )
                    is ConnectionStatus.Searching ->
                        ProgressScreen(stringResource(R.string.searching_for, status.host.name), onCancel)
                    is ConnectionStatus.Busy ->
                        ProgressScreen(stringResource(R.string.host_busy_retrying, status.host.name), onCancel)
                    is ConnectionStatus.Idle, is ConnectionStatus.Failed -> HostListScreen(
                        failure = (status as? ConnectionStatus.Failed),
                        state = state,
                        onHostClick = onHostClick,
                        onRescan = onRescan,
                        onManualConnect = onManualConnect,
                    )
                }
            }
        }
    }
}
