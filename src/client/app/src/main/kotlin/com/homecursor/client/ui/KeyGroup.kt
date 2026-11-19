package com.homecursor.client.ui

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.homecursor.client.R
import com.homecursor.client.protocol.SpecialKey

/** The kinds of key the UI tells apart: by colour on the keyboard and in a shortcut, and as sections of the key picker. */
enum class KeyGroup(@param:StringRes val title: Int) {
    Modifier(R.string.key_group_modifiers),
    Function(R.string.key_group_function),
    Letter(R.string.key_group_letters),
    Digit(R.string.key_group_digits),
    Other(R.string.key_group_other),
}

fun SpecialKey.group(): KeyGroup = when {
    isModifier -> KeyGroup.Modifier
    code in SpecialKey.F1.code..SpecialKey.F12.code -> KeyGroup.Function
    code in SpecialKey.A.code..SpecialKey.Z.code -> KeyGroup.Letter
    code in SpecialKey.D0.code..SpecialKey.D9.code -> KeyGroup.Digit
    else -> KeyGroup.Other
}

/** The container and content colours of a key at rest: modifiers stand out, function keys recede. */
@Composable
fun SpecialKey.restingColors(): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (group()) {
        KeyGroup.Modifier -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyGroup.Function -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        KeyGroup.Letter, KeyGroup.Digit, KeyGroup.Other -> colors.surfaceContainerHighest to colors.onSurface
    }
}
