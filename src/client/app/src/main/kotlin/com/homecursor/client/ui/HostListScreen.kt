package com.homecursor.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.discovery.DiscoveredHost
import com.homecursor.client.session.ConnectionStatus
import com.homecursor.client.ui.theme.HomeCursorTheme
import com.homecursor.client.ui.theme.Spacing

private val APP_MARK_SIZE = 44.dp
private val HOST_ICON_SIZE = 40.dp
private val EMPTY_ICON_SIZE = 72.dp
private val SCAN_INDICATOR_SIZE = 20.dp

/** The PCs found on the network (each a card), an error banner if the last attempt failed, and a manual address entry. */
@Composable
internal fun HostListScreen(
    failure: ConnectionStatus.Failed?,
    state: HomeUiState,
    onHostClick: (DiscoveredHost) -> Unit,
    onRescan: () -> Unit,
    onManualConnect: (String) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppHeader()

        if (failure != null) ErrorBanner(failureMessage(failure.failure, failure.host.name))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                stringResource(R.string.hosts_on_network),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.isScanning) {
                val scanning = stringResource(R.string.scanning)
                CircularProgressIndicator(
                    modifier = Modifier.size(SCAN_INDICATOR_SIZE).semantics { contentDescription = scanning },
                    strokeWidth = 2.dp,
                )
            }
            TextButton(onClick = onRescan, enabled = !state.isScanning) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.rescan))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.hosts.isEmpty() && !state.isScanning) {
                item { EmptyState() }
            }
            items(state.hosts, key = { it.address }) { host ->
                HostCard(host, onClick = { onHostClick(host) })
            }
        }

        ManualEntry(onManualConnect)
    }
}

@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconBadge(size = APP_MARK_SIZE, shape = MaterialTheme.shapes.medium)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
    }
}

/** A PC icon on a tinted background: the app mark, and the avatar of a host. */
@Composable
private fun IconBadge(size: Dp, shape: Shape) {
    Box(
        modifier = Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_computer),
            contentDescription = null,
            modifier = Modifier.size(size / 2),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun HostCard(host: DiscoveredHost, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            IconBadge(size = HOST_ICON_SIZE, shape = CircleShape)
            Column(modifier = Modifier.weight(1f)) {
                Text(host.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    host.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (host.isBusy) InUseBadge()
        }
    }
}

@Composable
private fun InUseBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(R.string.in_use),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(modifier = Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(
                painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(
            modifier = Modifier.size(EMPTY_ICON_SIZE).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_computer),
                contentDescription = null,
                modifier = Modifier.size(EMPTY_ICON_SIZE / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.no_hosts_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ManualEntry(onConnect: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val submit = { invalid = !onConnect(text) }

    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        Button(
            onClick = { submit() },
            modifier = Modifier.padding(top = LABEL_OVERHANG).height(OutlinedTextFieldDefaults.MinHeight),
        ) {
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

private val previewHosts = listOf(
    DiscoveredHost("192.168.1.20", "LIVING-ROOM-PC", 47800, 47801, isBusy = false),
    DiscoveredHost("192.168.1.31", "STUDY-LAPTOP", 47800, 47801, isBusy = true),
)

@Preview(name = "Hosts, light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HostListLightPreview() = HostListPreview(darkTheme = false, state = HomeUiState(hosts = previewHosts))

@Preview(name = "Hosts, dark", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HostListDarkPreview() = HostListPreview(darkTheme = true, state = HomeUiState(hosts = previewHosts))

@Preview(name = "Error, light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HostListErrorLightPreview() = HostListPreview(
    darkTheme = false,
    state = HomeUiState(hosts = previewHosts),
    failure = ConnectionStatus.Failed(HostEndpoint("192.168.1.20", "LIVING-ROOM-PC"), ConnectionFailure.Unreachable),
)

@Preview(name = "Empty, dark", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HostListEmptyDarkPreview() = HostListPreview(darkTheme = true, state = HomeUiState())

@Composable
private fun HostListPreview(darkTheme: Boolean, state: HomeUiState, failure: ConnectionStatus.Failed? = null) {
    HomeCursorTheme(darkTheme = darkTheme) {
        Surface {
            Box(modifier = Modifier.padding(Spacing.lg)) {
                HostListScreen(failure, state, onHostClick = {}, onRescan = {}, onManualConnect = { true })
            }
        }
    }
}
