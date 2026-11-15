package com.homecursor.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.input.TouchPoint
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.storage.TrackpadSettings
import kotlinx.coroutines.flow.Flow

/**
 * The connected screen: a large touch pad with a strip of buttons (left click, right click, menu) beside it. The
 * strip is at the bottom in portrait and at the side in landscape, so the pad keeps its width where the thumbs are.
 * The menu opens the settings and the disconnect actions.
 *
 * Stateless: it reports touches and button presses, and the view model decides what they mean.
 */
@Composable
fun TrackpadScreen(
    host: HostEndpoint,
    settings: TrackpadSettings,
    clickFeedback: Flow<Unit>,
    onTouch: (timeMillis: Long, pointers: List<TouchPoint>) -> Unit,
    onTouchCancelled: () -> Unit,
    onButtonPressed: (MouseButton) -> Unit,
    onButtonReleased: (MouseButton) -> Unit,
    onSensitivityChanged: (Float) -> Unit,
    onNaturalScrollChanged: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    KeepScreenOn()
    ClickHaptics(clickFeedback)
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val buttons = @Composable { vertical: Boolean ->
            ButtonStrip(vertical, onButtonPressed, onButtonReleased, onMenu = { menuOpen = true })
        }
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackpadSurface(onTouch, onTouchCancelled, Modifier.weight(1f).fillMaxHeight())
                buttons(true)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackpadSurface(onTouch, onTouchCancelled, Modifier.weight(1f).fillMaxWidth())
                buttons(false)
            }
        }
    }

    if (menuOpen) {
        SettingsSheet(
            host = host,
            settings = settings,
            onSensitivityChanged = onSensitivityChanged,
            onNaturalScrollChanged = onNaturalScrollChanged,
            onDisconnect = onDisconnect,
            onForget = onForget,
            onDismiss = { menuOpen = false },
        )
    }
}

/**
 * The area that follows the fingers. It reports every finger that is down on each touch event and consumes the
 * events, so nothing else reacts to them. If it leaves the screen while fingers are down (rotation, a lost
 * connection), the touches are reported as cancelled so a held drag button is released.
 */
@Composable
private fun TrackpadSurface(
    onTouch: (Long, List<TouchPoint>) -> Unit,
    onTouchCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentOnCancelled by rememberUpdatedState(onTouchCancelled)
    val description = stringResource(R.string.trackpad_description)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val down = event.changes.filter { it.pressed }
                                .map { TouchPoint(it.id.value.toInt(), it.position.x, it.position.y) }
                            currentOnTouch(event.changes.maxOf { it.uptimeMillis }, down)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } finally {
                    currentOnCancelled()
                }
            },
    )
}

@Composable
private fun ButtonStrip(
    vertical: Boolean,
    onPressed: (MouseButton) -> Unit,
    onReleased: (MouseButton) -> Unit,
    onMenu: () -> Unit,
) {
    val menuDescription = stringResource(R.string.menu)
    val menu = @Composable { modifier: Modifier ->
        TextButton(onClick = onMenu, modifier = modifier.semantics { contentDescription = menuDescription }) {
            Text("⋮", style = MaterialTheme.typography.headlineMedium)
        }
    }
    if (vertical) {
        Column(modifier = Modifier.width(120.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PadButton(stringResource(R.string.left_button), MouseButton.Left, onPressed, onReleased, Modifier.weight(1f).fillMaxWidth())
            PadButton(stringResource(R.string.right_button), MouseButton.Right, onPressed, onReleased, Modifier.weight(1f).fillMaxWidth())
            menu(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadButton(stringResource(R.string.left_button), MouseButton.Left, onPressed, onReleased, Modifier.weight(1f).fillMaxHeight())
            PadButton(stringResource(R.string.right_button), MouseButton.Right, onPressed, onReleased, Modifier.weight(1f).fillMaxHeight())
            menu(Modifier.fillMaxHeight())
        }
    }
}

/**
 * A mouse button. It is down for exactly as long as a finger is on it, so holding it and dragging on the pad with
 * another finger drags. [onReleased] also runs if the button leaves the screen while pressed.
 */
@Composable
private fun PadButton(
    label: String,
    button: MouseButton,
    onPressed: (MouseButton) -> Unit,
    onReleased: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPressed by rememberUpdatedState(onPressed)
    val currentOnReleased by rememberUpdatedState(onReleased)
    var isDown by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDown) colors.primary else colors.primaryContainer)
            .pointerInput(button) {
                detectTapGestures(
                    onPress = {
                        isDown = true
                        currentOnPressed(button)
                        try {
                            tryAwaitRelease()
                        } finally {
                            isDown = false
                            currentOnReleased(button)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (isDown) colors.onPrimary else colors.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    host: HostEndpoint,
    settings: TrackpadSettings,
    onSensitivityChanged: (Float) -> Unit,
    onNaturalScrollChanged: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(stringResource(R.string.connected_to, host.name), style = MaterialTheme.typography.titleLarge)
                Text(host.address, style = MaterialTheme.typography.bodyMedium)
            }

            Column {
                Text(stringResource(R.string.pointer_speed, settings.sensitivity), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.sensitivity,
                    onValueChange = onSensitivityChanged,
                    valueRange = TrackpadSettings.MIN_SENSITIVITY..TrackpadSettings.MAX_SENSITIVITY,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.natural_scroll), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.natural_scroll_summary), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.naturalScroll, onCheckedChange = onNaturalScrollChanged)
            }

            Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect)) }
            TextButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forget_host)) }
        }
    }
}

/** Keeps the screen on while the pad is shown: a trackpad that dims after 30 seconds of use is useless. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ClickHaptics(clickFeedback: Flow<Unit>) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(clickFeedback) {
        clickFeedback.collect { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
    }
}
