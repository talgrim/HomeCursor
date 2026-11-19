package com.homecursor.client.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette: teal (primary), indigo (secondary), amber (tertiary, used for status) on cool neutrals.
// Every text/background pair is at least WCAG AA (4.5:1) and outlines at least 3:1 against the surface.

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0F6E73),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEFF0),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4355B2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF0A1A5C),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2D1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFA),
    onBackground = Color(0xFF171D1D),
    surface = Color(0xFFF6FAFA),
    onSurface = Color(0xFF171D1D),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBFC8C8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F5),
    surfaceContainer = Color(0xFFEAEFEF),
    surfaceContainerHigh = Color(0xFFE5E9E9),
    surfaceContainerHighest = Color(0xFFDFE3E4),
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FD4D9),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFFBDEFF0),
    secondary = Color(0xFFB8C3FF),
    onSecondary = Color(0xFF1B2C78),
    secondaryContainer = Color(0xFF2F3F8C),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFFFFB86C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE3E3),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE3E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C8),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303636),
)
