package com.homecursor.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.input.KeyLabels
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.shortcuts.DefaultShortcuts
import com.homecursor.client.shortcuts.Shortcut
import com.homecursor.client.ui.theme.HomeCursorTheme
import com.homecursor.client.ui.theme.Spacing

/**
 * The shortcut list (portrait). Tapping a shortcut runs it on the host. "Edit" switches the list to managing mode:
 * edit, delete and move each shortcut, add a new one, or bring back the default ones.
 *
 * Stateless apart from the managing/editor state, which is only about what is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsSheet(
    shortcuts: List<Shortcut>,
    onRun: (Shortcut) -> Unit,
    onAdd: (name: String, keys: List<SpecialKey>) -> Unit,
    onUpdate: (id: String, name: String, keys: List<SpecialKey>) -> Unit,
    onDelete: (id: String) -> Unit,
    onMove: (id: String, offset: Int) -> Unit,
    onRestoreDefaults: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onDismiss: () -> Unit,
) {
    var managing by rememberSaveable { mutableStateOf(false) }
    // The id of the shortcut being edited, or "" for a new one; null when the editor is closed.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val canRestore = DefaultShortcuts.all.any { default -> shortcuts.none { it.id == default.id } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShortcutsContent(
            shortcuts = shortcuts,
            managing = managing,
            canRestore = canRestore,
            onToggleManaging = { managing = !managing },
            onRun = onRun,
            onEdit = { editingId = it.id },
            onAdd = { editingId = NEW_SHORTCUT },
            onDelete = onDelete,
            onMove = onMove,
            onRestoreDefaults = onRestoreDefaults,
            onOpenKeyboard = onOpenKeyboard,
        )
    }

    editingId?.let { id ->
        val existing = shortcuts.firstOrNull { it.id == id }
        // The shortcut can vanish while its editor is open (deleted elsewhere): then there is nothing to edit.
        if (id == NEW_SHORTCUT || existing != null) {
            ShortcutEditor(
                initialName = existing?.name.orEmpty(),
                initialKeys = existing?.keys.orEmpty(),
                onSave = { name, keys ->
                    if (existing == null) onAdd(name, keys) else onUpdate(existing.id, name, keys)
                    editingId = null
                },
                onDismiss = { editingId = null },
            )
        } else {
            editingId = null
        }
    }
}

private const val NEW_SHORTCUT = ""

/** What is inside the sheet: separate from the sheet itself so that it can be previewed. */
@Composable
private fun ShortcutsContent(
    shortcuts: List<Shortcut>,
    managing: Boolean,
    canRestore: Boolean,
    onToggleManaging: () -> Unit,
    onRun: (Shortcut) -> Unit,
    onEdit: (Shortcut) -> Unit,
    onAdd: () -> Unit,
    onDelete: (id: String) -> Unit,
    onMove: (id: String, offset: Int) -> Unit,
    onRestoreDefaults: () -> Unit,
    onOpenKeyboard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.shortcuts),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (managing) {
                FilledTonalButton(onClick = onToggleManaging) { Text(stringResource(R.string.shortcuts_done)) }
            } else {
                TextButton(onClick = onToggleManaging) { Text(stringResource(R.string.shortcuts_edit)) }
            }
        }
        Button(onClick = onOpenKeyboard, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.open_keyboard))
        }

        shortcuts.forEachIndexed { index, shortcut ->
            ShortcutRow(
                shortcut = shortcut,
                managing = managing,
                canMoveUp = index > 0,
                canMoveDown = index < shortcuts.lastIndex,
                onRun = { onRun(shortcut) },
                onEdit = { onEdit(shortcut) },
                onDelete = { onDelete(shortcut.id) },
                onMove = { onMove(shortcut.id, it) },
            )
        }

        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.shortcut_add))
        }
        if (managing && canRestore) {
            TextButton(onClick = onRestoreDefaults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.shortcuts_restore_defaults))
            }
        }
    }
}

/**
 * A shortcut as a card: its name and keys. A tap runs it; in managing mode the card is not tappable and shows the
 * move, edit and delete buttons instead, each named after the shortcut so that a screen reader can tell them apart.
 */
@Composable
private fun ShortcutRow(
    shortcut: Shortcut,
    managing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (offset: Int) -> Unit,
) {
    val content = @Composable {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Text(shortcut.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(Spacing.xs))
            KeyCombo(shortcut.keys)
            if (managing) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ManageButton(R.drawable.ic_arrow_up, stringResource(R.string.shortcut_move_up_named, shortcut.name), canMoveUp) { onMove(-1) }
                    ManageButton(R.drawable.ic_arrow_down, stringResource(R.string.shortcut_move_down_named, shortcut.name), canMoveDown) { onMove(1) }
                    ManageButton(R.drawable.ic_edit, stringResource(R.string.shortcut_edit_named, shortcut.name), true, onEdit)
                    ManageButton(R.drawable.ic_delete, stringResource(R.string.shortcut_delete_named, shortcut.name), true, onDelete)
                }
            }
        }
    }
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    if (managing) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = color) { content() }
    } else {
        Surface(onClick = onRun, modifier = Modifier.fillMaxWidth(), shape = shape, color = color) { content() }
    }
}

@Composable
private fun ManageButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/** Every key, by group, in the order of the groups: what the key picker offers. */
private val keysByGroup: List<Pair<KeyGroup, List<SpecialKey>>> =
    KeyGroup.entries.map { group -> group to SpecialKey.entries.filter { it.group() == group } }

/**
 * Name field, the shortcut being built (drawn as keycaps, in the order the keys were picked), and a chip for every
 * key, grouped so that Ctrl is not lost among forty letters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutEditor(
    initialName: String,
    initialKeys: List<SpecialKey>,
    onSave: (name: String, keys: List<SpecialKey>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    // Saved as the key codes: a SpecialKey itself is not saveable.
    var codes by rememberSaveable { mutableStateOf(initialKeys.map { it.code }) }
    val keys = codes.mapNotNull(SpecialKey::fromCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialKeys.isEmpty()) R.string.shortcut_add else R.string.shortcut_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.shortcut_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(
                        modifier = Modifier.heightIn(min = EDITOR_PREVIEW_MIN_HEIGHT).padding(Spacing.md),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (keys.isEmpty()) {
                            Text(
                                stringResource(R.string.shortcut_pick_keys),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            KeyCombo(keys)
                        }
                    }
                }
                for ((group, groupKeys) in keysByGroup) {
                    Text(
                        stringResource(group.title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        for (key in groupKeys) {
                            FilterChip(
                                selected = key in keys,
                                onClick = { codes = if (key in keys) codes - key.code else codes + key.code },
                                label = { Text(KeyLabels.of(key)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.ifBlank { KeyLabels.combination(keys) }, keys) },
                enabled = keys.isNotEmpty(),
            ) { Text(stringResource(R.string.shortcut_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val EDITOR_PREVIEW_MIN_HEIGHT = 48.dp

@Preview(name = "Browsing, light", showBackground = true, widthDp = 360)
@Composable
private fun ShortcutsLightPreview() = ShortcutsPreview(darkTheme = false, managing = false)

@Preview(name = "Managing, dark", showBackground = true, widthDp = 360)
@Composable
private fun ShortcutsManagingDarkPreview() = ShortcutsPreview(darkTheme = true, managing = true)

@Composable
private fun ShortcutsPreview(darkTheme: Boolean, managing: Boolean) {
    HomeCursorTheme(darkTheme = darkTheme) {
        Surface {
            ShortcutsContent(
                shortcuts = DefaultShortcuts.all.take(4),
                managing = managing,
                canRestore = true,
                onToggleManaging = {},
                onRun = {},
                onEdit = {},
                onAdd = {},
                onDelete = {},
                onMove = { _, _ -> },
                onRestoreDefaults = {},
                onOpenKeyboard = {},
            )
        }
    }
}
