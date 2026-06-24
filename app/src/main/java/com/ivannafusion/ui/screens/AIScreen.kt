/*
 * IVANNA-FUSION — AIScreen v2.0 ELITE
 * Motor IA con SpectralClassifier real: genre, BPM, energía por banda, centroide.
 * Estado persistente via DSPState. UI glassmorphism oscuro.
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */
package com.ivannafusion.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ivannafusion.AudioEngine
import com.ivannafusion.DSPState
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun AIScreen(audioEngine: AudioEngine, onBack: () -> Unit) {

    // ── Estado persistente desde DSPState ────────────────────────────────────
    var aiEnabled    by remember { mutableStateOf(DSPState.aiEnabled) }
    var autoAdapt    by remember { mutableStateOf(DSPState.aiAutoAdapt) }
    var sensitivity  by remember { mutableFloatStateOf(DSPState.aiSensitivity) }

    // ── Métricas en tiempo real ───────────────────────────────────────────────
    var genre        by remember { mutableStateOf("Analizando...") }
    var confidence   by remember { mutableFloatStateOf(0f) }
    var bpm          by remember { mutableFloatStateOf(0f) }
    var centroid     by remember { mutableFloatStateOf(0f) }
    var bass         by remember { mutableFloatStateOf(0f) }
    var mid          by remember { mutableFloatStateOf(0f) }
    var high         by remember { mutableFloatStateOf(0f) }
    var rmsDb        by remember { mutableFloatStateOf(-60f) }
    var spectrum     by remember { mutableStateOf(FloatArray(32)) }
    var isLive       by remember { mutableStateOf(false) }

    // ── Polling de métricas reales ────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            val loaded = audioEngine.isAiClassifierLoaded()
            if (loaded && aiEnabled) {
                genre      = audioEngine.aiGetDetectedGenre()
                confidence = audioEngine.AudioStubs.aiGetConfidence()
                bpm        = audioEngine.aiGetTempo()
                centroid   = audioEngine.aiGetCentroidHz()
                bass       = audioEngine.aiGetBassEnergy()
                mid        = audioEngine.aiGetMidEnergy()
                high       = audioEngine.aiGetHighEnergy()
                rmsDb      = audioEngine.aiGetRmsDb()
                spectrum   = audioEngine.aiGetSpectrum()
                isLive     = true
            } else {
                isLive = false
            }
            delay(200)
        }
    }

    // ── Color dinámico por género ─────────────────────────────────────────────
    val genreColor by animateColorAsState(
        when {
            genre.contains("Música") || genre.contains("Music")  -> AccentCyan
            genre.contains("Habla")  || genre.contains("Speech") -> AccentEmerald
            genre.contains("Elect")                              -> AccentViolet
            genre.contains("Silen")  || genre.contains("Silence")-> TextSecondary
            else                                                 -> AccentAmber
        },
        animationSpec = tween(600), label = "genreColor"
    )

    // ── BPM pulsing animation ─────────────────────────────────────────────────
    val bpmPulse = remember { Animatable(1f) }
    LaunchedEffect(bpm) {
        if (bpm > 60f && isLive) {
            val intervalMs = (60000f / bpm).toLong().coerceIn(200L, 2000L)
            while (true) {
                bpmPulse.animateTo(1.15f, tween(80))
                bpmPulse.animateTo(1f, tween(120))
                delay(intervalMs - 200)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        IVANNAHeader(title = "MOTOR IA", subtitle = "Análisis espectral en tiempo real") {
            StatusChip(
                text = if (isLive) "LIVE" else "STANDBY",
                color = if (isLive) genreColor else TextSecondary
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan)
            }
        }

        // ── Spectrum Visualizer ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(120.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundTertiary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    drawSpectrum(spectrum, genreColor)
                }
                // Grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineColor = Color.White.copy(alpha = 0.04f)
                    for (i in 1..3) {
                        val y = size.height * i / 4f
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
                    }
                }
                Text(
                    "20Hz                    1kHz                  20kHz",
                    color = TextSecondary.copy(alpha = 0.4f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                )
            }
        }

        // ── Genre + Confidence ───────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundTertiary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            genre.uppercase(),
                            color = genreColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "%.0f%% confianza".format(confidence * 100f),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // BPM pulsante
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(bpmPulse.value)
                            .background(genreColor.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, genreColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "%.0f".format(if (bpm > 0f) bpm else 0f),
                                color = genreColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "BPM",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Confidence bar
                LinearProgressIndicator(
                    progress = { confidence },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = genreColor,
                    trackColor = genreColor.copy(alpha = 0.15f)
                )

                Spacer(Modifier.height(12.dp))

                // Centroide + RMS
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("CENTROIDE", formatHz(centroid), genreColor)
                    MetricPill("RMS", "%.1f dB".format(rmsDb), AccentAmber)
                    MetricPill("ZCR", "%.3f".format(audioEngine.aiGetZcr()), AccentViolet)
                }
            }
        }

        // ── Band Energy ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundTertiary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "ENERGÍA POR BANDA",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BandBar("GRAVES\n20–250Hz", bass, Color(0xFF4AEEFF), Modifier.weight(1f))
                    BandBar("MEDIOS\n250–4kHz", mid, Color(0xFF44FF88), Modifier.weight(1f))
                    BandBar("AGUDOS\n4–16kHz", high, Color(0xFFFF8C44), Modifier.weight(1f))
                }
            }
        }

        // ── AI Controls ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundTertiary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "CONFIGURACIÓN IA",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MOTOR IA", color = TextPrimary, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace)
                        Text("Análisis espectral FFT en tiempo real",
                            color = TextSecondary, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                    IVANNAToggle(checked = aiEnabled, onCheckedChange = {
                        aiEnabled = it
                        DSPState.aiEnabled = it
                        DSPState.saveAI()
                        audioEngine.aiSetEnabled(it)
                    })
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AUTO-ADAPTACIÓN", color = TextPrimary, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace)
                        Text("DSP se ajusta automáticamente al contenido",
                            color = TextSecondary, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                    IVANNAToggle(checked = autoAdapt, onCheckedChange = {
                        autoAdapt = it
                        DSPState.aiAutoAdapt = it
                        audioEngine.aiSetAutoAdapt(it)
                    })
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                Text("SENSIBILIDAD DE DETECCIÓN", color = TextSecondary, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Slider(
                        value = sensitivity,
                        onValueChange = {
                            sensitivity = it
                            DSPState.aiSensitivity = it
                            DSPState.saveAI()
                            audioEngine.aiSetSensitivity(it)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentViolet,
                            activeTrackColor = AccentViolet
                        )
                    )
                    Text(
                        "%.0f%%".format(sensitivity * 100),
                        color = AccentViolet,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Modo IA
                Text("MODO", color = TextSecondary, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Adaptativo" to "adaptive", "Música" to "music",
                        "Habla" to "speech", "Plano" to "flat").forEach { (label, mode) ->
                        val sel = DSPState.aiMode == mode
                        Button(
                            onClick = {
                                DSPState.aiMode = mode
                                DSPState.saveAI()
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sel) AccentViolet.copy(alpha=0.25f)
                                else BackgroundSecondary
                            ),
                            contentPadding = PaddingValues(2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = if (sel) AccentViolet else TextSecondary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Componentes internos ───────────────────────────────────────────────────────

@Composable
private fun BandBar(label: String, level: Float, color: Color, modifier: Modifier = Modifier) {
    val animLevel by animateFloatAsState(level, tween(150), label = "band")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(color.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animLevel.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.3f))),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
            )
            Text(
                "%.0f%%".format(animLevel * 100f),
                color = color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 8.sp,
            fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}

private fun formatHz(hz: Float): String = when {
    hz >= 1000f -> "%.1fkHz".format(hz / 1000f)
    else        -> "%.0fHz".format(hz)
}

private fun DrawScope.drawSpectrum(spectrum: FloatArray, color: Color) {
    if (spectrum.isEmpty()) return
    val w = size.width; val h = size.height
    val bw = w / spectrum.size
    spectrum.forEachIndexed { i, v ->
        val barH = (v * h).coerceAtLeast(2f)
        val x = i * bw
        drawRect(
            brush = Brush.verticalGradient(
                listOf(color, color.copy(alpha = 0.2f)),
                startY = h - barH, endY = h
            ),
            topLeft = Offset(x + 1f, h - barH),
            size = androidx.compose.ui.geometry.Size(bw - 2f, barH)
        )
    }
}
