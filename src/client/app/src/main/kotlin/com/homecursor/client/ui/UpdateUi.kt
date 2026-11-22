package com.homecursor.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.ui.theme.HomeCursorTheme
import com.homecursor.client.ui.theme.Spacing
import com.homecursor.client.update.AppVersion
import com.homecursor.client.update.UpdateInfo
import com.homecursor.client.update.UpdateState
import com.homecursor.client.update.UpdateUiState

/** What the update screens can ask for. */
class UpdateCallbacks(
    val onCheck: () -> Unit,
    val onUpdate: () -> Unit,
    val onOpenInstallSettings: () -> Unit,
    val onChecksEnabledChanged: (Boolean) -> Unit,
) {
    companion object {
        val None = UpdateCallbacks({}, {}, {}, {})
    }
}

private val PROGRESS_SIZE = 20.dp

/** The card on the PC list that announces a release and lets the user install it. Nothing is shown without a release. */
@Composable
internal fun UpdateBanner(state: UpdateUiState, callbacks: UpdateCallbacks, modifier: Modifier = Modifier) {
    val update = state.offered ?: return
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                stringResource(R.string.update_available, update.version.toString()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            UpdateStatusAndAction(state, callbacks, onContainer = true)
        }
    }
}

/** The update part of the settings sheet: version, status, the action that fits it and the switch for automatic checks. */
@Composable
internal fun UpdateSection(state: UpdateUiState, callbacks: UpdateCallbacks, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column {
            Text(stringResource(R.string.updates), style = MaterialTheme.typography.bodyLarge)
            state.installed?.let {
                Text(
                    stringResource(R.string.current_version, it.toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        UpdateStatusAndAction(state, callbacks, onContainer = false)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.check_for_updates_automatically), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.check_for_updates_automatically_summary), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.checksEnabled, onCheckedChange = callbacks.onChecksEnabledChanged)
        }
    }
}

@Composable
private fun UpdateStatusAndAction(state: UpdateUiState, callbacks: UpdateCallbacks, onContainer: Boolean) {
    val textColor = if (onContainer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val update = state.offered

    when (val current = state.state) {
        is UpdateState.Checking, is UpdateState.Downloading -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE), strokeWidth = 2.dp)
                Text(
                    stringResource(if (current is UpdateState.Checking) R.string.update_checking else R.string.update_downloading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }

        is UpdateState.NeedsPermission -> {
            Text(stringResource(R.string.update_needs_permission), style = MaterialTheme.typography.bodyMedium, color = textColor)
            Button(onClick = callbacks.onOpenInstallSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_allow_install))
            }
        }

        else -> {
            when (current) {
                is UpdateState.UpToDate ->
                    Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyMedium, color = textColor)
                is UpdateState.Failed -> Text(
                    stringResource(if (current.update == null) R.string.update_check_failed else R.string.update_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onContainer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }

            if (update != null) {
                Button(onClick = callbacks.onUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_to, update.version.toString()))
                }
            } else {
                OutlinedButton(onClick = callbacks.onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.check_for_updates))
                }
            }
        }
    }
}

private val PREVIEW_UPDATE = UpdateInfo(AppVersion(1, 4, 0), "https://example.test/a.apk", "0".repeat(64), 1_500_000)

@Preview(name = "Banner, light", showBackground = true, widthDp = 360)
@Composable
private fun UpdateBannerLightPreview() = UpdateBannerPreview()

@Preview(name = "Banner, dark", showBackground = true, widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpdateBannerDarkPreview() = UpdateBannerPreview()

@Composable
private fun UpdateBannerPreview() {
    HomeCursorTheme {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            UpdateBanner(UpdateUiState(UpdateState.Available(PREVIEW_UPDATE)), UpdateCallbacks.None)
            UpdateBanner(UpdateUiState(UpdateState.NeedsPermission(PREVIEW_UPDATE)), UpdateCallbacks.None)
            UpdateBanner(UpdateUiState(UpdateState.Downloading(PREVIEW_UPDATE)), UpdateCallbacks.None)
        }
    }
}

@Preview(name = "Section, light", showBackground = true, widthDp = 360)
@Composable
private fun UpdateSectionPreview() {
    HomeCursorTheme {
        UpdateSection(
            UpdateUiState(UpdateState.UpToDate, installed = AppVersion(1, 3, 0)),
            UpdateCallbacks.None,
            Modifier.padding(Spacing.lg),
        )
    }
}
