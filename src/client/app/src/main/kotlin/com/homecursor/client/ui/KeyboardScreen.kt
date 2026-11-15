package com.homecursor.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.input.KeyLabels
import com.homecursor.client.input.KeyboardLayout
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.ui.theme.Spacing

/**
 * The full-screen on-screen keyboard (landscape). A mode switch picks between typing key by key
 * ([KeyboardMode.RealTime]: the QWERTY keyboard) and typing a text on the phone that is sent as a whole
 * ([KeyboardMode.Compose]). Stateless: the view model decides what a tap means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    mode: KeyboardMode,
    heldModifiers: Set<SpecialKey>,
    onModeChanged: (KeyboardMode) -> Unit,
    onKeyTapped: (SpecialKey) -> Unit,
    onTextSent: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                val modes = KeyboardMode.entries
                modes.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == mode,
                        onClick = { onModeChanged(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(stringResource(if (entry == KeyboardMode.RealTime) R.string.keyboard_mode_keys else R.string.keyboard_mode_text))
                    }
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.keyboard_close)) }
        }

        when (mode) {
            KeyboardMode.RealTime -> KeyGrid(heldModifiers, onKeyTapped, Modifier.weight(1f).fillMaxWidth())
            KeyboardMode.Compose -> ComposePanel(heldModifiers, onKeyTapped, onTextSent, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** The QWERTY keys, one row per [KeyboardLayout] row, all rows and keys sharing the height and width evenly. */
@Composable
fun KeyGrid(
    held: Set<SpecialKey>,
    onKeyTapped: (SpecialKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        for (row in KeyboardLayout.rows) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                for (cell in row) {
                    val cellModifier = Modifier.weight(cell.width).fillMaxHeight()
                    if (cell.key == null) {
                        Box(cellModifier)
                    } else {
                        KeyCap(cell.key, cell.key in held, onKeyTapped, cellModifier)
                    }
                }
            }
        }
    }
}

/** The gap between keys, and between the rows of keys. */
private val KEY_GAP = Spacing.xs

/** Height of the row of action keys next to the compose field. */
private val ACTION_ROW_HEIGHT = 48.dp

/**
 * One key. A held modifier (and a key while it is pressed) is shown in the primary colour; otherwise the colour
 * depends on its [KeyGroup].
 */
@Composable
private fun KeyCap(
    key: SpecialKey,
    isHeld: Boolean,
    onTapped: (SpecialKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlighted = isHeld || isPressed
    val (restingContainer, restingContent) = key.restingColors()
    val container by animateColorAsState(if (highlighted) colors.primary else restingContainer, label = "keyContainer")
    val content by animateColorAsState(if (highlighted) colors.onPrimary else restingContent, label = "keyContent")

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, role = Role.Button) { onTapped(key) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            KeyLabels.of(key),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Keys that have no text to type with the phone's keyboard, next to the field. */
private val composeActionKeys = listOf(
    SpecialKey.Escape, SpecialKey.Tab, SpecialKey.Backspace, SpecialKey.Enter,
    SpecialKey.Left, SpecialKey.Down, SpecialKey.Up, SpecialKey.Right,
)

/** Compose mode: a text field (the phone's own keyboard, so any language) and a button that sends it all at once. */
@Composable
private fun ComposePanel(
    held: Set<SpecialKey>,
    onKeyTapped: (SpecialKey) -> Unit,
    onTextSent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.keyboard_text_hint)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Button(
                onClick = {
                    onTextSent(text)
                    text = ""
                },
                enabled = text.isNotEmpty(),
                modifier = Modifier.padding(top = LABEL_OVERHANG).fillMaxHeight(),
            ) { Text(stringResource(R.string.keyboard_send)) }
        }
        Row(modifier = Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            for (key in composeActionKeys) {
                KeyCap(key, key in held, onKeyTapped, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
