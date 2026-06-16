package com.oliva.notes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Warm Editorial — adapted from recipe-manager DESIGN.md.
// Olive #6E7A45 as primary accent on warm paper #FBF7F0.
// Fraunces for titles, DM Sans for body (set via AppTypography).
// Dark scheme is provisional.

private val LightColors = lightColorScheme(
    primary = WarmOlive,
    onPrimary = WarmCard,
    secondaryContainer = WarmOliveSoft,
    onSecondaryContainer = WarmOlive,
    background = WarmPaper,
    onBackground = WarmInk,
    surface = WarmCard,
    onSurface = WarmInk,
    onSurfaceVariant = WarmMuted,
    outline = WarmLine,
    error = WarmError,
    onError = WarmCard,
)

private val DarkColors = darkColorScheme(
    primary = WarmOliveDark,
    onPrimary = WarmPaperDark,
    secondaryContainer = WarmOliveSoftDark,
    onSecondaryContainer = WarmOliveDark,
    background = WarmPaperDark,
    onBackground = WarmInkDark,
    surface = WarmCardDark,
    onSurface = WarmInkDark,
    onSurfaceVariant = WarmMutedDark,
    outline = WarmLineDark,
    error = WarmError,
)

@Composable
fun MeetingMinuteTheme(
    themeMode: com.oliva.notes.app.data.preferences.ThemeMode = com.oliva.notes.app.data.preferences.ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        com.oliva.notes.app.data.preferences.ThemeMode.LIGHT -> false
        com.oliva.notes.app.data.preferences.ThemeMode.DARK -> true
        com.oliva.notes.app.data.preferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
