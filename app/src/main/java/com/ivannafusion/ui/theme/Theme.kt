package com.ivannafusion.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Obsidian = Color(0xFF08080B)
val Carbon = Color(0xFF0F0F14)
val Graphite = Color(0xFF16161D)
val Steel = Color(0xFF1E1E28)
val Titanium = Color(0xFF282836)
val Platinum = Color(0xFFE8E8F0)
val Silver = Color(0xFFB8B8C8)
val Chrome = Color(0xFF8888A0)
val PrecisionCyan = Color(0xFF00E5FF)
val QuantumPurple = Color(0xFFB388FF)
val SignalGreen = Color(0xFF00E676)
val WarningAmber = Color(0xFFFFAB00)
val CriticalRed = Color(0xFFFF1744)

val MasterGradient = listOf(Color(0xFF00E5FF), Color(0xFF00B8D4), Color(0xFF0091EA))

private val MasterColorScheme = darkColorScheme(
    primary = PrecisionCyan, secondary = QuantumPurple, tertiary = SignalGreen,
    background = Obsidian, surface = Carbon, surfaceVariant = Graphite,
    onPrimary = Obsidian, onSecondary = Color.White, onBackground = Platinum,
    onSurface = Platinum, onSurfaceVariant = Silver, outline = Chrome
)

val MasterTypography = Typography(
    displayLarge = TextStyle(FontFamily.SansSerif, FontWeight.Black, 57.sp, 64.sp),
    headlineLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 32.sp, 40.sp),
    headlineMedium = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 28.sp, 36.sp),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 22.sp, 28.sp),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 16.sp, 24.sp),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 16.sp, 24.sp),
    bodyMedium = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 14.sp, 20.sp),
    labelLarge = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 14.sp, 20.sp),
    labelMedium = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 12.sp, 16.sp)
)

@Composable
fun IVANNATheme(content: @Composable () -> Unit) {
    MaterialTheme(MasterColorScheme, MasterTypography, content)
}
