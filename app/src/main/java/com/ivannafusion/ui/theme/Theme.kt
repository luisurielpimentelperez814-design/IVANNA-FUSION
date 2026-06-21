package com.ivannafusion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = BackgroundPrimary,
    primaryContainer = Color(0xFF003D47),
    onPrimaryContainer = AccentCyan,
    secondary = AccentMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A0E2B),
    onSecondaryContainer = AccentMagenta,
    tertiary = AccentViolet,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = BackgroundSecondary,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundTertiary,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle
)

@Composable
fun IVANNAFusionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
