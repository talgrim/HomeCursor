package com.homecursor.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.ui.theme.HomeCursorTheme
import com.homecursor.client.ui.theme.Spacing

private val SPINNER_SIZE = 96.dp
private val SPINNER_ICON_SIZE = 36.dp

/** Shown while connecting, searching for the saved PC or waiting for it to become free: a spinner around a PC, and Cancel. */
@Composable
internal fun ProgressScreen(message: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Box(modifier = Modifier.size(SPINNER_SIZE), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(SPINNER_SIZE))
            Icon(
                painterResource(R.drawable.ic_computer),
                contentDescription = null,
                modifier = Modifier.size(SPINNER_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Preview(name = "Light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ProgressScreenLightPreview() = ProgressScreenPreview(darkTheme = false)

@Preview(name = "Dark", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ProgressScreenDarkPreview() = ProgressScreenPreview(darkTheme = true)

@Composable
private fun ProgressScreenPreview(darkTheme: Boolean) {
    HomeCursorTheme(darkTheme = darkTheme) {
        Surface { ProgressScreen(message = "Connecting to LIVING-ROOM-PC…", onCancel = {}) }
    }
}
