package com.kissansevaai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Slate950,
    primaryContainer = Emerald700,
    onPrimaryContainer = Emerald100,
    secondary = Blue500,
    onSecondary = Slate950,
    secondaryContainer = Blue600,
    onSecondaryContainer = Slate100,
    tertiary = Amber500,
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    error = Red500,
    onError = Slate950,
    outline = Slate700,
    outlineVariant = Slate800,
)

@Composable
fun KissanSevaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = KissanTypography,
        content = content,
    )
}
