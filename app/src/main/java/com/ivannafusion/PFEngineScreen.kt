/*
 * IVANNA-FUSION TRASCENDENTAL v2.0
 * PFEngineScreen.kt — Control del PF-ENGINE: amp models, spectral params, presets
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */
package com.ivannafusion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PFEngineScreen(navController: NavController) {
    var ampModel    by remember { mutableIntStateOf(4) }  // 4 = Bypass
    var alpha       by remember { mutableFloatStateOf(1.0f) }
    var beta        by remember { mutableFloatStateOf(0.3f) }
    var delta       by remember { mutableFloatStateOf(0.4f) }
    var sigma       by remember { mutableFloatStateOf(0.5f) }
    var drive       by remember { mutableFloatStateOf(1.0f) }
    var wet         by remember { mutableFloatStateOf(0.6f) }
    var lowGain     by remember { mutableFloatStateOf(0.0f) }
    var midGain     by remember { mutableFloatStateOf(0.0f) }
    var highGain    by remember { mutableFloatStateOf(0.0f) }
    var presence    by remember { mutableFloatStateOf(0.0f) }
    var sag         by remember { mutableFloatStateOf(0.1f) }
    var evoActive   by remember { mutableStateOf(false) }
    var barCount    by remember { mutableIntStateOf(0) }
    var feedbackMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val ampColors = listOf(
        Color(0xFFFF4444), Color(0xFF44AAFF), Color(0xFFFFCC44),
        Color(0xFFFF8C00), Color(0xFF888888)
    )
    val ampNames = AMP_NAMES

    // Evo auto-tick (sincroniza barras con AudioEngine)
    LaunchedEffect(evoActive) {
        if (evoActive) {
            while (evoActive) {
                AudioEngine.pfEvoTick(barCount)
                barCount++
                delay(500)  // 1 compás = 500 ms @ ~120 BPM
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060610))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text("⚡ PF-ENGINE PRO MAX NEXT", color = Color(0xFFFF8C00), fontSize = 16.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text("v3.0.0 — Amp Modeling + Evolution + FFT Learning",
            color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text("© 2025 GORE TNS — Luis Uriel Pimentel Pérez",
            color = Color.DarkGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

        Spacer(Modifier.height(14.dp))

        // ── Amp Model selector ──────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("AMP MODEL", color = Color(0xFFFF8C00), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ampNames.forEachIndexed { i, name ->
                        Button(
                            onClick = {
                                ampModel = i
                                AudioEngine.pfSetAmp(i)
                                scope.launch {
                                    feedbackMsg = "Amp: ${ampNames[i]}"
                                    delay(1500); feedbackMsg = ""
                                }
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ampModel == i)
                                    ampColors[i] else Color(0xFF1A1A2E)
                            ),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text(name, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                color = if (ampModel == i) Color.Black else ampColors[i])
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Spectral params (alpha, beta, delta, sigma) ─────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("PARÁMETROS ESPECTRALES", color = Color(0xFFFF8C00), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                PFSlider("α alpha — Spectral Tilt", alpha, 0.5f..2.0f, ampColors[ampModel]) {
                    alpha = it; AudioEngine.pfSetParam("alpha", it)
                }
                PFSlider("β beta — Harmonic Density", beta, 0.0f..1.0f, ampColors[ampModel]) {
                    beta = it; AudioEngine.pfSetParam("beta", it)
                }
                PFSlider("δ delta — Distortion Depth", delta, 0.0f..1.0f, ampColors[ampModel]) {
                    delta = it; AudioEngine.pfSetParam("delta", it)
                }
                PFSlider("σ sigma — Spatial Width", sigma, 0.0f..1.0f, ampColors[ampModel]) {
                    sigma = it; AudioEngine.pfSetParam("sigma", it)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Drive + Wet/Dry ─────────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("DRIVE / WET-DRY", color = Color(0xFFFF8C00), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                PFSlider("Drive: %.2f".format(drive), drive, 0.0f..4.0f, Color(0xFFFF4444)) {
                    drive = it; AudioEngine.pfSetParam("drive", it)
                }
                PFSlider("Wet: %.0f%%".format(wet * 100), wet, 0.0f..1.0f, Color(0xFF44AAFF)) {
                    wet = it; AudioEngine.pfSetParam("wet", it)
                }
                PFSlider("Sag: %.2f".format(sag), sag, 0.0f..0.5f, Color(0xFFFFCC44)) {
                    sag = it; AudioEngine.pfSetParam("sag", it)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── EQ 3 bandas ─────────────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("EQ 3 BANDAS + PRESENCE", color = Color(0xFFFF8C00), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                PFSlider("Low: %+.1f dB".format(lowGain), lowGain + 12f, 0f..24f, Color(0xFF44FF88),
                    valueDisplay = "%+.1f dB".format(lowGain)) {
                    lowGain = it - 12f; AudioEngine.pfSetParam("low", lowGain)
                }
                PFSlider("Mid: %+.1f dB".format(midGain), midGain + 12f, 0f..24f, Color(0xFF44AAFF),
                    valueDisplay = "%+.1f dB".format(midGain)) {
                    midGain = it - 12f; AudioEngine.pfSetParam("mid", midGain)
                }
                PFSlider("High: %+.1f dB".format(highGain), highGain + 12f, 0f..24f, Color(0xFFFFCC44),
                    valueDisplay = "%+.1f dB".format(highGain)) {
                    highGain = it - 12f; AudioEngine.pfSetParam("high", highGain)
                }
                PFSlider("Presence: %+.1f dB".format(presence), presence + 6f, 0f..12f, Color(0xFFFF8C00),
                    valueDisplay = "%+.1f dB".format(presence)) {
                    presence = it - 6f; AudioEngine.pfSetParam("presence", presence)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Evolution Curve ─────────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("CURVA DE EVOLUCIÓN", color = Color(0xFFCC44FF), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AIMetric("BAR", "$barCount", Color(0xFFCC44FF))
                    AIMetric("FASE",
                        when {
                            barCount < 16 -> "BASELINE"
                            barCount < 32 -> "BUILD"
                            barCount < 48 -> "PEAK"
                            else          -> "DECAY"
                        },
                        when {
                            barCount < 16 -> Color.Gray
                            barCount < 32 -> Color(0xFF44AAFF)
                            barCount < 48 -> Color(0xFFFF4444)
                            else          -> Color(0xFFCC44FF)
                        })
                    AIMetric("STATUS",
                        if (evoActive) "▶ ON" else "⏹ OFF",
                        if (evoActive) Color(0xFF44FF88) else Color.Gray)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { evoActive = !evoActive },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (evoActive) Color(0xFF880022) else Color(0xFF226611)
                        )
                    ) {
                        Text(if (evoActive) "⏹ STOP EVO" else "▶ START EVO",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { barCount = 0; AudioEngine.pfEvoReset() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Text("↺ RESET", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Feedback
        if (feedbackMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(feedbackMsg, color = Color(0xFFFF8C00), fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0E00), RoundedCornerShape(8.dp))
                    .padding(10.dp))
        }

        // Nav
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { navController.navigate("presets") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF44AAFF))) {
                Text("PRESETS", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = { navController.navigate("monitor") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF44FF88))) {
                Text("MONITOR", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun PFSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    valueDisplay: String? = null,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(valueDisplay ?: "%.2f".format(value),
                color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}
