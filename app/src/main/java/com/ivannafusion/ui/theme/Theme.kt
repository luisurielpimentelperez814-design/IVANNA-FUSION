package com.ivannafusion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RackColorScheme = darkColorScheme(
    primary             = AccentCyan,
    onPrimary           = BackgroundPrimary,
    primaryContainer    = Color(0xFF2E2815),
    onPrimaryContainer  = AccentCyan,
    secondary           = AccentEmerald,
    onSecondary         = BackgroundPrimary,
    secondaryContainer  = Color(0xFF1C2218),
    onSecondaryContainer= AccentEmerald,
    tertiary            = AccentViolet,
    background          = BackgroundPrimary,
    onBackground        = TextPrimary,
    surface             = BackgroundSecondary,
    onSurface           = TextPrimary,
    surfaceVariant      = BackgroundTertiary,
    onSurfaceVariant    = TextSecondary,
    outline             = BorderSubtle,
    outlineVariant      = BorderSubtle,
    error               = SignalHot,
    onError             = Color(0xFF1A0800)
)

@Composable
fun IVANNAFusionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RackColorScheme,
        typography  = Typography,
        content     = content
    )
}
