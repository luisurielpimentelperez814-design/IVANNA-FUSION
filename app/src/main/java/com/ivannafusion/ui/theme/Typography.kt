package com.ivannafusion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Tipografía de rack:
 * - Titles: Black + letterSpacing amplio → simula serigrafía industrial
 * - Numbers/values: Monospace → siempre, como hardware real
 * - Body: Default condensado con tracking reducido
 */
val Typography = Typography(
    // Títulos de sección (ECUALIZADOR, MOTOR IA, etc.)
    titleLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Black,
        fontSize     = 20.sp,
        lineHeight   = 24.sp,
        letterSpacing= 3.sp
    ),
    titleMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 15.sp,
        lineHeight   = 20.sp,
        letterSpacing= 1.5.sp
    ),
    titleSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 13.sp,
        lineHeight   = 18.sp,
        letterSpacing= 1.sp
    ),
    // Labels (etiquetas de panel)
    labelLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Bold,
        fontSize     = 14.sp,
        letterSpacing= 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        letterSpacing= 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Medium,
        fontSize     = 10.sp,
        letterSpacing= 2.sp
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing= 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 17.sp,
        letterSpacing= 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Normal,
        fontSize     = 10.sp,
        lineHeight   = 14.sp,
        letterSpacing= 0.sp
    ),
    // Display: números grandes (BPM, dB, Hz)
    displayLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Black,
        fontSize     = 36.sp,
        letterSpacing= (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Bold,
        fontSize     = 26.sp,
        letterSpacing= 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Medium,
        fontSize     = 18.sp,
        letterSpacing= 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Black,
        fontSize     = 22.sp,
        letterSpacing= 4.sp
    ),
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Black,
        fontSize     = 16.sp,
        letterSpacing= 3.sp
    ),
    headlineSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 13.sp,
        letterSpacing= 2.sp
    )
)
