package com.kissansevaai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = DeepEmerald,
    onPrimary = Color.White,
    primaryContainer = GreenTint,
    onPrimaryContainer = Color(0xFF00210A),
    inversePrimary = PrimaryFixedDim,
    secondary = SecondaryOrange,
    onSecondary = Color.White,
    secondaryContainer = AmberTint,
    onSecondaryContainer = Color(0xFF2F1500),
    tertiary = TertiaryGreen,
    onTertiary = Color.White,
    tertiaryContainer = GreenTintLight,
    onTertiaryContainer = Color(0xFF002113),
    background = SurfaceBg,
    onBackground = TextCharcoal,
    surface = CardWhite,
    onSurface = TextCharcoal,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextMuted,
    surfaceTint = PrimaryGreen,
    outline = BorderMedium,
    outlineVariant = BorderLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

val DarkColorScheme = darkColorScheme(
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
fun KissanSevaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = KissanTypography,
        content = content,
    )
}
