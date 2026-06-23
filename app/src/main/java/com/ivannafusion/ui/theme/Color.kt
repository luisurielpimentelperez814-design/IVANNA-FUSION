package com.ivannafusion.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * IVANNA FUSION — Sistema de color v2
 * Dirección: Instrumentación de precisión (rack de estudio, no "dark app neón")
 *
 * Paleta: 4 valores semánticos + fondos escalonados.
 * Negro cálido carbón, papel envejecido, latón apagado, verde musgo.
 */

// ── Fondos (carbón cálido, no azul-negro) ─────────────────────────────────────
val BackgroundPrimary   = Color(0xFF171410)   // carbón oscuro cálido
val BackgroundSecondary = Color(0xFF1F1C14)   // superficie levantada
val BackgroundTertiary  = Color(0xFF272318)   // panel de control

// ── Texto (papel envejecido, no blanco LED) ───────────────────────────────────
val TextPrimary   = Color(0xFFE5DFC8)         // "papel" de instrumento
val TextSecondary = Color(0xFF9A9378)         // lectura desgastada
val TextTertiary  = Color(0xFF68624F)         // serigrafía apagada

// ── Acento único — latón apagado (usado con moderación) ──────────────────────
val AccentCyan   = Color(0xFFC9A85C)          // latón / gold muted  ← acento principal
val TextAccent   = Color(0xFFC9A85C)

// ── Señal (verde musgo = saludable, cobre = clip) ─────────────────────────────
val AccentEmerald   = Color(0xFF6E8467)       // verde musgo — señal OK
val SignalCool      = Color(0xFF6E8467)
val AccentMagenta   = Color(0xFFB85540)       // cobre-rojo — peligro/clip
val SignalHot       = Color(0xFFB85540)
val AccentRose      = Color(0xFFB85540)

// ── Tonos de soporte (variantes del latón / neutros) ─────────────────────────
val AccentViolet = Color(0xFF9A9378)          // igual que TextSecondary (datos secundarios)
val AccentAmber  = Color(0xFFC9A85C)          // igual que acento principal
val SignalWarm   = Color(0xFFB07840)          // ámbar desgastado (advertencia)
val SignalMute   = Color(0xFF68624F)          // apagado

// ── Knob / control físico ─────────────────────────────────────────────────────
val KnobBackground  = Color(0xFF1A1710)       // cuerpo del knob
val KnobForeground  = Color(0xFFC9A85C)       // indicador de latón
val KnobTrack       = Color(0xFF3A3626)       // pista gastada

// ── Bordes (ranura de rack) ───────────────────────────────────────────────────
val BorderSubtle    = Color(0xFF3A3626)       // borde de ranura metálica

// ── Extras ────────────────────────────────────────────────────────────────────
val MasterGradient  = Color(0xFF171410)
val Carbon          = Color(0xFF0F0E0A)
val PrecisionCyan   = Color(0xFFC9A85C)       // redirigido a latón
val Graphite        = Color(0xFF272318)
val Titanium        = Color(0xFF4A4538)
val Platinum        = Color(0xFFE5DFC8)
val Silver          = Color(0xFF9A9378)
