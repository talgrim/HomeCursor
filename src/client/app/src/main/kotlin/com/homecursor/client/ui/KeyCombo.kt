package com.homecursor.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homecursor.client.input.KeyLabels
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.ui.theme.Spacing

/**
 * A key combination drawn as small keycaps joined by "+", coloured like the keyboard's keys. Assistive technology
 * reads it as one phrase, for example "Alt + Enter", and not as separate keycaps and plus signs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyCombo(keys: List<SpecialKey>, modifier: Modifier = Modifier) {
    val description = KeyLabels.combination(keys)
    FlowRow(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, key ->
            if (index > 0) {
                Text("+", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            KeyChip(key)
        }
    }
}

@Composable
private fun KeyChip(key: SpecialKey) {
    val (container, content) = key.restingColors()
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            KeyLabels.of(key),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.clearAndSetSemantics {}.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
