/*
 * IVANNA FUSION — DashboardScreen v2
 * Estética: rack de mastering, no "dark app genérica".
 * Panel de instrumentación real: VU de aguja, espectro FFT real, tipografía serigráfica.
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */
package com.ivannafusion.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ivannafusion.AudioEngine
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(audioEngine: AudioEngine, onNavigate: (String) -> Unit) {

    // ── Métricas en tiempo real ───────────────────────────────────────────────
    var levelDb    by remember { mutableFloatStateOf(-60f) }
    var spectrum   by remember { mutableStateOf(FloatArray(32)) }
    var correlation by remember { mutableFloatStateOf(1f) }
    var latency    by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var fitness    by remember { mutableFloatStateOf(0f) }
    var genre      by remember { mutableStateOf("—") }
    var bpm        by remember { mutableFloatStateOf(0f) }
    var isLive     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Dar tiempo al SpectralClassifier para la primera muestra
        delay(600)
        isLive = true
        while (true) {
            levelDb     = audioEngine.aiGetRmsDb()
            spectrum    = audioEngine.aiGetSpectrum()
            correlation = audioEngine.getCorrelation()
            latency     = audioEngine.getLatencyMicros().toInt()
            generation  = audioEngine.getGeneration()
            fitness     = audioEngine.getBestFitness()
            genre       = audioEngine.aiGetDetectedGenre()
            bpm         = audioEngine.aiGetTempo()
            delay(120)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header tipo panel de rack ─────────────────────────────────────────
        RackHeader(isLive = isLive, genre = genre, bpm = bpm)

        // ── VU METER (elemento firma) ─────────────────────────────────────────
        RackPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Etiqueta del canal
                VerticalLabel("INPUT")
                VUNeedleMeter(
                    levelDb  = levelDb,
                    modifier = Modifier.weight(1f).height(148.dp)
                )
                VerticalLabel("L+R")
            }
        }

        // ── Analizador de espectro (datos FFT reales) ─────────────────────────
        RackPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                PanelLabel("ANALIZADOR  FFT  —  32 BANDAS")
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                ) {
                    drawRackSpectrum(spectrum)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("20", "100", "500", "2K", "10K", "20K").forEach {
                        Text(it, color = TextTertiary, fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // ── Medidores de precisión (3 stat cells) ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PrecisionCell("LATENCIA", "${latency}μs", AccentCyan, Modifier.weight(1f))
            PrecisionCell("GENERACIÓN", "$generation", AccentEmerald, Modifier.weight(1f))
            PrecisionCell("FITNESS", "%.3f".format(fitness), AccentEmerald, Modifier.weight(1f))
        }

        // ── Correlación estéreo ───────────────────────────────────────────────
        RackPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            PanelLabel("CORRELACIÓN  ESTÉREO")
            Spacer(Modifier.height(8.dp))
            CorrelationMeter(correlation = correlation)
        }

        // ── Panel de acceso rápido (estilo patchbay) ──────────────────────────
        RackPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            PanelLabel("MÓDULOS")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PatchPoint(Icons.Default.GraphicEq, "EQ / DINÁMICA",
                    "10 bandas paramétricas  ·  compresor  ·  exciter") { onNavigate("effects") }
                PatchPoint(Icons.Default.AutoAwesome, "MOTOR IA",
                    "Clasificador espectral FFT  ·  BPM  ·  auto-adaptación") { onNavigate("ai") }
                PatchPoint(Icons.Default.LibraryMusic, "PRESETS",
                    "Amp models  ·  Marshall / Fender / Vox / 70s Rock") { onNavigate("presets") }
                PatchPoint(Icons.Default.Tune, "PF-ENGINE  v3",
                    "Amp modeling  ·  curva de evolución  ·  parámetros α β δ σ") { onNavigate("pfengine") }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Componentes internos del Dashboard ────────────────────────────────────────

@Composable
private fun RackHeader(isLive: Boolean, genre: String, bpm: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1F1C14), BackgroundPrimary)
                )
            )
            .border(BorderStroke(0.5.dp, BorderSubtle.copy(alpha = 0.4f)))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "IVANNA FUSION",
                        color     = TextPrimary,
                        fontSize  = 18.sp,
                        fontWeight= FontWeight.Black,
                        fontFamily= FontFamily.Default,
                        letterSpacing = 5.sp
                    )
                    Text(
                        "DSP  ·  MASTERING  ·  SNAPDRAGON",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                }
                // Indicador de estado — LED de rack
                RackLed(isLive)
            }
            Spacer(Modifier.height(8.dp))
            // Barra de info en tiempo real
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0D08), RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InlineMetric("MODO", genre.take(12).uppercase())
                InlineMetric("BPM", if (bpm > 0f) "%.0f".format(bpm) else "—")
                InlineMetric("VER", "v2.0")
                InlineMetric("ABI", "arm64-v8a")
            }
        }
    }
}

@Composable
private fun RackLed(active: Boolean) {
    val pulse by rememberInfiniteTransition(label = "led").animateFloat(
        initialValue = if (active) 0.5f else 1f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing), RepeatMode.Reverse
        ), label = "led_alpha"
    )
    Box(
        Modifier
            .size(10.dp)
            .background(
                if (active) AccentEmerald.copy(alpha = pulse)
                else TextTertiary.copy(alpha = 0.4f),
                RoundedCornerShape(50)
            )
    )
}

@Composable
private fun RackPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(BackgroundSecondary, RoundedCornerShape(3.dp))
            .border(BorderStroke(0.5.dp, BorderSubtle.copy(alpha = 0.5f)), RoundedCornerShape(3.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text          = text,
        color         = TextTertiary,
        fontSize      = 9.sp,
        fontWeight    = FontWeight.Medium,
        fontFamily    = FontFamily.Default,
        letterSpacing = 2.sp
    )
}

@Composable
private fun VerticalLabel(text: String) {
    Text(
        text          = text,
        color         = TextTertiary,
        fontSize      = 7.sp,
        fontFamily    = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier      = Modifier
            .padding(horizontal = 4.dp)
            .graphicsLayer { rotationZ = -90f }
            .width(60.dp)
    )
}

@Composable
private fun InlineMetric(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextTertiary, fontSize = 8.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text(value, color = AccentCyan, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrecisionCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F0D08), RoundedCornerShape(2.dp))
            .border(BorderStroke(0.5.dp, BorderSubtle.copy(alpha = 0.4f)), RoundedCornerShape(2.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextTertiary, fontSize = 7.sp,
            fontFamily = FontFamily.Default, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PatchPoint(
    icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF0F0D08))
            .border(BorderStroke(0.5.dp, BorderSubtle.copy(alpha = 0.3f)), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Punto de patch (círculo de conexión)
        Box(
            Modifier
                .size(8.dp)
                .background(AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(50))
                .border(BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)), RoundedCornerShape(50))
        )
        Spacer(Modifier.width(10.dp))
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 12.sp,
                fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
            Text(subtitle, color = TextTertiary, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
        }
        Text("→", color = TextTertiary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun DrawScope.drawRackSpectrum(spectrum: FloatArray) {
    if (spectrum.isEmpty()) return
    val w   = size.width
    val h   = size.height
    val bw  = w / spectrum.size.toFloat()

    // Grid horizontal
    for (i in 1..3) {
        val y = h * i / 4f
        drawLine(BorderSubtle.copy(alpha = 0.2f), Offset(0f, y), Offset(w, y), 0.5f)
    }

    // Barras FFT — verde musgo en zona segura, cobre en zona caliente
    spectrum.forEachIndexed { i, v ->
        val bh     = (v * h).coerceAtLeast(1f)
        val x      = i * bw
        val isHot  = v > 0.85f
        drawRect(
            brush = Brush.verticalGradient(
                if (isHot)
                    listOf(Color(0xFFB85540), Color(0xFF6E3020))
                else
                    listOf(AccentCyan, AccentCyan.copy(alpha = 0.25f)),
                startY = h - bh, endY = h
            ),
            topLeft = Offset(x + 1f, h - bh),
            size    = androidx.compose.ui.geometry.Size(bw - 2f, bh)
        )
        // Peak hold dot
        if (v > 0.05f) drawRect(
            color   = if (isHot) Color(0xFFE5DFC8) else AccentCyan,
            topLeft = Offset(x + 1f, h - bh - 2f),
            size    = androidx.compose.ui.geometry.Size(bw - 2f, 1.5f)
        )
    }
}
