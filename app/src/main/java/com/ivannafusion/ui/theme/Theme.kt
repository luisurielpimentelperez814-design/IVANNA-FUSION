package com.ivannafusion.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00BCD4),
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFF4CAF50),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun IVANNATheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
