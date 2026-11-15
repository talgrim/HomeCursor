package com.homecursor.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homecursor.client.R
import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.session.ConnectionStatus

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
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // The trackpad gets almost the whole screen: touches outside it would be lost.
            val margin = if (state.status is ConnectionStatus.Connected) 8.dp else 16.dp
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

@Composable
private fun ProgressScreen(message: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun HostListScreen(
    failure: ConnectionStatus.Failed?,
    state: HomeUiState,
    onHostClick: (DiscoveredHost) -> Unit,
    onRescan: () -> Unit,
    onManualConnect: (String) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        if (failure != null) {
            Text(
                failureMessage(failure.failure, failure.host.name),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.hosts_on_network),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.isScanning) CircularProgressIndicator(modifier = Modifier.height(24.dp))
            TextButton(onClick = onRescan, enabled = !state.isScanning) { Text(stringResource(R.string.rescan)) }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.hosts.isEmpty() && !state.isScanning) {
                item { Text(stringResource(R.string.no_hosts_found), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.hosts, key = { it.address }) { host ->
                ListItem(
                    headlineContent = { Text(host.name) },
                    supportingContent = {
                        Text(if (host.isBusy) "${host.address} · ${stringResource(R.string.in_use)}" else host.address)
                    },
                    modifier = Modifier.clickable { onHostClick(host) },
                )
                HorizontalDivider()
            }
        }

        ManualEntry(onManualConnect)
    }
}

@Composable
private fun ManualEntry(onConnect: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val submit = { invalid = !onConnect(text) }

    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; invalid = false },
            label = { Text(stringResource(R.string.manual_address)) },
            isError = invalid,
            supportingText = { if (invalid) Text(stringResource(R.string.manual_address_invalid)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { submit() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.connect))
        }
    }
}

@Composable
private fun failureMessage(failure: ConnectionFailure, host: String): String = stringResource(
    when (failure) {
        ConnectionFailure.Busy -> R.string.failure_busy
        ConnectionFailure.UnsupportedVersion -> R.string.failure_unsupported_version
        ConnectionFailure.Unreachable -> R.string.failure_unreachable
        ConnectionFailure.Timeout -> R.string.failure_timeout
        ConnectionFailure.ProtocolError -> R.string.failure_protocol_error
        ConnectionFailure.HostClosed -> R.string.failure_host_closed
    },
    host,
)
