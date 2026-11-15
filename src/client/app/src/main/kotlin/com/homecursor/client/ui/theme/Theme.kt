package com.homecursor.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** App theme: the brand palette, typography and shapes. Follows the system dark mode unless [darkTheme] is given. */
@Composable
fun HomeCursorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = HomeCursorTypography,
        shapes = HomeCursorShapes,
        content = content,
    )
}
