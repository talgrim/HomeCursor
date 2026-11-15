package com.homecursor.client.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homecursor.client.connection.HostEndpoint

/**
 * What is shown while connected: the trackpad, with the keyboard button leading to the shortcut list (portrait) or
 * the full-screen on-screen keyboard (landscape). Opening the keyboard from portrait turns the phone to landscape
 * (the activity follows [KeyboardViewModel.isOpen]); until it has turned, the trackpad stays on screen.
 */
@Composable
fun ConnectedScreen(
    host: HostEndpoint,
    trackpadViewModel: TrackpadViewModel,
    keyboardViewModel: KeyboardViewModel,
    shortcutsViewModel: ShortcutsViewModel,
    tutorialViewModel: TutorialViewModel,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    val settings by trackpadViewModel.settings.collectAsStateWithLifecycle()
    val keyboardOpen by keyboardViewModel.isOpen.collectAsStateWithLifecycle()
    val mode by keyboardViewModel.mode.collectAsStateWithLifecycle()
    val heldModifiers by keyboardViewModel.heldModifiers.collectAsStateWithLifecycle()
    val shortcuts by shortcutsViewModel.shortcuts.collectAsStateWithLifecycle()
    val tutorialStep by tutorialViewModel.stepIndex.collectAsStateWithLifecycle()
    var shortcutsOpen by rememberSaveable { mutableStateOf(false) }
    val tutorialTargets = remember { TutorialTargets() }

    // The first time the trackpad is shown after connecting, the tour starts by itself.
    LaunchedEffect(Unit) { tutorialViewModel.onTrackpadShown() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // The shortcut list is a portrait thing: turning the phone sideways closes it.
        LaunchedEffect(landscape) { if (landscape) shortcutsOpen = false }

        if (keyboardOpen && landscape) {
            KeyboardScreen(
                mode = mode,
                heldModifiers = heldModifiers,
                onModeChanged = keyboardViewModel::onModeChanged,
                onKeyTapped = keyboardViewModel::onKeyTapped,
                onTextSent = keyboardViewModel::onTextSent,
                onClose = keyboardViewModel::close,
            )
        } else {
            TrackpadScreen(
                host = host,
                settings = settings,
                clickFeedback = trackpadViewModel.clickFeedback,
                onTouch = trackpadViewModel::onTouch,
                onTouchCancelled = trackpadViewModel::onTouchCancelled,
                onButtonPressed = trackpadViewModel::onButtonPressed,
                onButtonReleased = trackpadViewModel::onButtonReleased,
                onSensitivityChanged = trackpadViewModel::onSensitivityChanged,
                onNaturalScrollChanged = trackpadViewModel::onNaturalScrollChanged,
                onKeyboard = { if (landscape) keyboardViewModel.open() else shortcutsOpen = true },
                onDisconnect = onDisconnect,
                onForget = onForget,
                tutorialTargets = tutorialTargets,
                onTutorial = tutorialViewModel::start,
            )
        }

        tutorialStep?.let { index ->
            val steps = TutorialSteps.all
            TutorialOverlay(
                step = steps[index],
                index = index,
                count = steps.size,
                targets = tutorialTargets,
                onNext = tutorialViewModel::next,
                onBack = tutorialViewModel::back,
                onSkip = tutorialViewModel::skip,
            )
        }

        if (shortcutsOpen && !landscape) {
            ShortcutsSheet(
                shortcuts = shortcuts,
                onRun = shortcutsViewModel::run,
                onAdd = shortcutsViewModel::add,
                onUpdate = shortcutsViewModel::update,
                onDelete = shortcutsViewModel::delete,
                onMove = shortcutsViewModel::move,
                onRestoreDefaults = shortcutsViewModel::restoreDefaults,
                onOpenKeyboard = {
                    shortcutsOpen = false
                    keyboardViewModel.open()
                },
                onDismiss = { shortcutsOpen = false },
            )
        }
    }
}
