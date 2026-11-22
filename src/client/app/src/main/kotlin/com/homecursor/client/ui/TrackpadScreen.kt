package com.homecursor.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.input.TouchPoint
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.storage.TrackpadSettings
import com.homecursor.client.ui.theme.Spacing
import com.homecursor.client.update.UpdateUiState
import kotlinx.coroutines.flow.Flow

/**
 * The connected screen: a large touch pad with a strip of buttons (left, middle and right button, keyboard) beside it. The
 * strip is at the bottom in portrait and on the left in landscape, so the pad keeps its width where the thumbs are.
 * The keyboard button opens the shortcuts or the on-screen keyboard (see [ConnectedScreen]); the gear in the upper
 * right corner of the pad opens the settings and the disconnect actions.
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
    onKeyboard: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    tutorialTargets: TutorialTargets,
    onTutorial: () -> Unit,
    updateState: UpdateUiState,
    updateCallbacks: UpdateCallbacks,
) {
    KeepScreenOn()
    ClickHaptics(clickFeedback)
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val buttons = @Composable { vertical: Boolean ->
            ButtonStrip(vertical, onButtonPressed, onButtonReleased, onKeyboard, tutorialTargets)
        }
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                buttons(true)
                PadWithSettings(host, onTouch, onTouchCancelled, { menuOpen = true }, updateState.offered != null, tutorialTargets, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PadWithSettings(host, onTouch, onTouchCancelled, { menuOpen = true }, updateState.offered != null, tutorialTargets, Modifier.weight(1f).fillMaxWidth())
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
            onTutorial = {
                menuOpen = false
                onTutorial()
            },
            updateState = updateState,
            updateCallbacks = updateCallbacks,
            onDismiss = { menuOpen = false },
        )
    }
}

/**
 * The touch pad with the connection chip in its upper left corner and the settings (gear) button in its upper right corner, away from the button strip (which is at
 * the bottom in portrait and on the left in landscape). The gear sits on top of the pad, so touches on it open the
 * settings and never reach the pad.
 */
@Composable
private fun PadWithSettings(
    host: HostEndpoint,
    onTouch: (Long, List<TouchPoint>) -> Unit,
    onTouchCancelled: () -> Unit,
    onSettings: () -> Unit,
    updateAvailable: Boolean,
    tutorialTargets: TutorialTargets,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.tutorialTarget(tutorialTargets, TutorialTarget.Pad)) {
        TrackpadSurface(onTouch, onTouchCancelled, Modifier.fillMaxSize())
        ConnectionChip(host.name, Modifier.align(Alignment.TopStart).padding(Spacing.md))
        IconButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).tutorialTarget(tutorialTargets, TutorialTarget.Settings),
        ) {
            // A dot on the gear tells that the settings hold an update.
            BadgedBox(badge = { if (updateAvailable) Badge() }) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shows which PC is controlled. It only draws: it has no pointer handling, so touches on it reach the pad below.
 * Amber, like the "in use" badge, so connection state stands apart from the teal controls.
 */
@Composable
private fun ConnectionChip(hostName: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.connected_to, hostName)
    Surface(
        modifier = modifier.widthIn(max = CHIP_MAX_WIDTH).semantics(mergeDescendants = true) { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(Modifier.size(CHIP_DOT_SIZE).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            Text(
                hostName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.large)
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

/** The middle button is used less than Left and Right, so it gets a smaller share of the strip. */
private const val MIDDLE_BUTTON_WEIGHT = 0.6f

/** The keyboard button is a small tonal square, so it is clear where it takes a tap. */
private val KEYBOARD_BUTTON_SIZE = 56.dp

/** Width of the strip in landscape and height of it in portrait. */
private val STRIP_WIDTH = 120.dp
private val STRIP_HEIGHT = 80.dp

private val CHIP_MAX_WIDTH = 180.dp
private val CHIP_DOT_SIZE = 8.dp

@Composable
private fun ButtonStrip(
    vertical: Boolean,
    onPressed: (MouseButton) -> Unit,
    onReleased: (MouseButton) -> Unit,
    onKeyboard: () -> Unit,
    targets: TutorialTargets,
) {
    val keyboard = @Composable { modifier: Modifier ->
        FilledTonalIconButton(
            onClick = onKeyboard,
            modifier = modifier.size(KEYBOARD_BUTTON_SIZE).tutorialTarget(targets, TutorialTarget.Keyboard),
            shape = MaterialTheme.shapes.large,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_keyboard), contentDescription = stringResource(R.string.open_keyboard))
        }
    }
    if (vertical) {
        Column(
            modifier = Modifier.width(STRIP_WIDTH).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PadButton(MouseButton.Left, onPressed, onReleased, Modifier.weight(1f).fillMaxWidth().tutorialTarget(targets, TutorialTarget.LeftButton))
            PadButton(
                MouseButton.Middle,
                onPressed,
                onReleased,
                Modifier.weight(MIDDLE_BUTTON_WEIGHT).fillMaxWidth().tutorialTarget(targets, TutorialTarget.MiddleButton),
            )
            PadButton(MouseButton.Right, onPressed, onReleased, Modifier.weight(1f).fillMaxWidth().tutorialTarget(targets, TutorialTarget.RightButton))
            keyboard(Modifier)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadButton(MouseButton.Left, onPressed, onReleased, Modifier.weight(1f).fillMaxHeight().tutorialTarget(targets, TutorialTarget.LeftButton))
            PadButton(
                MouseButton.Middle,
                onPressed,
                onReleased,
                Modifier.weight(MIDDLE_BUTTON_WEIGHT).fillMaxHeight().tutorialTarget(targets, TutorialTarget.MiddleButton),
            )
            PadButton(MouseButton.Right, onPressed, onReleased, Modifier.weight(1f).fillMaxHeight().tutorialTarget(targets, TutorialTarget.RightButton))
            keyboard(Modifier)
        }
    }
}

/**
 * A mouse button, drawn as a mouse with the button's zone filled (see [MouseGlyph]). It is down for exactly as long as
 * a finger is on it, so holding it and dragging on the pad with another finger drags. [onReleased] also runs if the
 * button leaves the screen while pressed. Assistive technology gets a button with a spoken name whose click is a
 * press and an immediate release, since it cannot hold a finger down.
 */
@Composable
private fun PadButton(
    button: MouseButton,
    onPressed: (MouseButton) -> Unit,
    onReleased: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPressed by rememberUpdatedState(onPressed)
    val currentOnReleased by rememberUpdatedState(onReleased)
    var isDown by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val isMiddle = button == MouseButton.Middle
    val restingColor = if (isMiddle) colors.secondaryContainer else colors.primaryContainer
    val restingContent = if (isMiddle) colors.onSecondaryContainer else colors.onPrimaryContainer
    val background by animateColorAsState(if (isDown) colors.primary else restingColor, label = "padButtonBackground")
    val content by animateColorAsState(if (isDown) colors.onPrimary else restingContent, label = "padButtonContent")
    val description = stringResource(
        when (button) {
            MouseButton.Left -> R.string.left_button
            MouseButton.Middle -> R.string.middle_button
            MouseButton.Right -> R.string.right_button
        },
    )

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick {
                    currentOnPressed(button)
                    currentOnReleased(button)
                    true
                }
            }
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
        MouseGlyph(button, content)
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
    onTutorial: () -> Unit,
    updateState: UpdateUiState,
    updateCallbacks: UpdateCallbacks,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.natural_scroll), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.natural_scroll_summary), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.naturalScroll, onCheckedChange = onNaturalScrollChanged)
            }

            Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onTutorial)) {
                Text(stringResource(R.string.tutorial), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.tutorial_summary), style = MaterialTheme.typography.bodySmall)
            }

            UpdateSection(updateState, updateCallbacks)

            Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect)) }
            TextButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forget_host)) }
        }
    }
}

/** Keeps the screen on while the pad is shown: a trackpad that dims after 30 seconds of use is useless. */
@Composable
internal fun KeepScreenOn() {
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
