package com.homecursor.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.homecursor.client.R
import com.homecursor.client.input.KeyLabels
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.shortcuts.DefaultShortcuts
import com.homecursor.client.shortcuts.Shortcut

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
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.shortcuts),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { managing = !managing }) {
                    Text(stringResource(if (managing) R.string.shortcuts_done else R.string.shortcuts_edit))
                }
            }
            Button(onClick = onOpenKeyboard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_keyboard))
            }

            shortcuts.forEachIndexed { index, shortcut ->
                HorizontalDivider()
                ShortcutRow(
                    shortcut = shortcut,
                    managing = managing,
                    canMoveUp = index > 0,
                    canMoveDown = index < shortcuts.lastIndex,
                    onRun = { onRun(shortcut) },
                    onEdit = { editingId = shortcut.id },
                    onDelete = { onDelete(shortcut.id) },
                    onMove = { onMove(shortcut.id, it) },
                )
            }

            HorizontalDivider()
            OutlinedButton(onClick = { editingId = NEW_SHORTCUT }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.shortcut_add))
            }
            if (managing && canRestore) {
                TextButton(onClick = onRestoreDefaults, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shortcuts_restore_defaults))
                }
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (managing) Modifier else Modifier.clickable(onClick = onRun))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(shortcut.name, style = MaterialTheme.typography.bodyLarge)
            Text(KeyLabels.combination(shortcut.keys), style = MaterialTheme.typography.bodySmall)
        }
        if (managing) {
            TextButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Text("↑") }
            TextButton(onClick = { onMove(1) }, enabled = canMoveDown) { Text("↓") }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.shortcuts_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.shortcut_delete)) }
        }
    }
}

/** Name field plus a chip for every key: the keys picked are the shortcut, shown as "Ctrl + C". */
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.shortcut_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (keys.isEmpty()) stringResource(R.string.shortcut_pick_keys) else KeyLabels.combination(keys),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (key in SpecialKey.entries) {
                        FilterChip(
                            selected = key in keys,
                            onClick = { codes = if (key in keys) codes - key.code else codes + key.code },
                            label = { Text(KeyLabels.of(key)) },
                        )
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
