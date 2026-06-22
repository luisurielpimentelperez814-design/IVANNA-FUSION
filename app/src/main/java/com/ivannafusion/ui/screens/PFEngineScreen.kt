package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivannafusion.AMP_NAMES
import com.ivannafusion.AudioEngine
import com.ivannafusion.PF_PRESETS
import com.ivannafusion.PFPreset
import com.ivannafusion.ui.components.IVANNAHeader
import com.ivannafusion.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PFEngineScreen(audioEngine: AudioEngine, onBack: () -> Unit) {
    var ampModel   by remember { mutableIntStateOf(4) }
    var drive      by remember { mutableFloatStateOf(1.0f) }
    var wet        by remember { mutableFloatStateOf(0.6f) }
    var alpha      by remember { mutableFloatStateOf(1.0f) }
    var delta      by remember { mutableFloatStateOf(0.4f) }
    var sigma      by remember { mutableFloatStateOf(0.5f) }
    var lowGain    by remember { mutableFloatStateOf(0.0f) }
    var midGain    by remember { mutableFloatStateOf(0.0f) }
    var highGain   by remember { mutableFloatStateOf(0.0f) }
    var presence   by remember { mutableFloatStateOf(0.0f) }
    var evoActive  by remember { mutableStateOf(false) }
    var barCount   by remember { mutableIntStateOf(0) }
    var activePreset by remember { mutableStateOf<PFPreset?>(null) }
    val scope      = rememberCoroutineScope()

    val ampColors = listOf(
        Color(0xFFFF4444), Color(0xFF44AAFF), Color(0xFFFFCC44),
        Color(0xFFFF8C00), Color(0xFF888888)
    )

    LaunchedEffect(evoActive) {
        if (evoActive) while (evoActive) {
            audioEngine.pfEvoTick(barCount++)
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        IVANNAHeader(title = "PF-ENGINE v3", subtitle = "Amp Modeling + Evolution") {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan)
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Presets rápidos ────────────────────────────────────────────
            Text("PRESETS", color = AccentCyan, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(PF_PRESETS) { preset ->
                    val isActive = activePreset?.name == preset.name
                    val c = Color(preset.color)
                    Button(
                        onClick = {
                            activePreset = preset
                            audioEngine.applyPFPreset(preset)
                            ampModel = preset.ampModel
                            drive = preset.drive; wet = preset.wet
                            alpha = preset.alpha; delta = preset.delta; sigma = preset.sigma
                            lowGain = preset.lowGain; midGain = preset.midGain
                            highGain = preset.highGain; presence = preset.presence
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) c.copy(alpha = 0.25f) else BackgroundTertiary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(preset.emoji, fontSize = 16.sp)
                            Text(preset.displayName.split(" ").first(),
                                color = if (isActive) c else TextSecondary,
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // ── Amp model ──────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundTertiary)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AMP MODEL", color = AccentAmber, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AMP_NAMES.forEachIndexed { i, name ->
                            Button(
                                onClick = { ampModel = i; audioEngine.pfSetAmp(i) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ampModel == i) ampColors[i]
                                    else BackgroundSecondary
                                ),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(name, fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                                    color = if (ampModel == i) Color.Black else ampColors[i])
                            }
                        }
                    }
                }
            }

            // ── Drive / Wet / Alpha ────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundTertiary)) {
                Column(modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("PARÁMETROS", color = AccentAmber, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    PFRow("Drive %.2f".format(drive), drive, 0f..4f, AccentMagenta) {
                        drive = it; audioEngine.pfSetParam("drive", it) }
                    PFRow("Wet %.0f%%".format(wet * 100), wet, 0f..1f, AccentCyan) {
                        wet = it; audioEngine.pfSetParam("wet", it) }
                    PFRow("α Tilt %.2f".format(alpha), alpha, 0.5f..2f, AccentViolet) {
                        alpha = it; audioEngine.pfSetParam("alpha", it) }
                    PFRow("δ Dist %.2f".format(delta), delta, 0f..1f, SignalHot) {
                        delta = it; audioEngine.pfSetParam("delta", it) }
                    PFRow("σ Width %.2f".format(sigma), sigma, 0f..1f, AccentEmerald) {
                        sigma = it; audioEngine.pfSetParam("sigma", it) }
                }
            }

            // ── EQ 3 bandas ────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundTertiary)) {
                Column(modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("EQ + PRESENCE", color = AccentAmber, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    PFRow("Low %+.1f dB".format(lowGain), lowGain + 12f, 0f..24f, AccentEmerald) {
                        lowGain = it - 12f; audioEngine.pfSetParam("low", lowGain) }
                    PFRow("Mid %+.1f dB".format(midGain), midGain + 12f, 0f..24f, AccentCyan) {
                        midGain = it - 12f; audioEngine.pfSetParam("mid", midGain) }
                    PFRow("High %+.1f dB".format(highGain), highGain + 12f, 0f..24f, AccentAmber) {
                        highGain = it - 12f; audioEngine.pfSetParam("high", highGain) }
                    PFRow("Presence %+.1f".format(presence), presence + 6f, 0f..12f, AccentMagenta) {
                        presence = it - 6f; audioEngine.pfSetParam("presence", presence) }
                }
            }

            // ── Evolution Curve ────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundTertiary)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("EVOLUTION CURVE", color = AccentViolet, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        EvoStat("BAR",    "$barCount", AccentViolet)
                        EvoStat("FASE",
                            when { barCount < 16 -> "BASE"; barCount < 32 -> "BUILD"
                                barCount < 48 -> "PEAK"; else -> "DECAY" },
                            when { barCount < 16 -> TextSecondary; barCount < 32 -> AccentCyan
                                barCount < 48 -> SignalHot; else -> AccentViolet })
                        EvoStat("STATUS",
                            if (evoActive) "▶ ON" else "⏹ OFF",
                            if (evoActive) AccentEmerald else TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { evoActive = !evoActive },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (evoActive) SignalHot else AccentEmerald
                            )
                        ) { Text(if (evoActive) "⏹ STOP" else "▶ START",
                            fontFamily = FontFamily.Monospace) }
                        OutlinedButton(
                            onClick = { barCount = 0; audioEngine.pfEvoReset() },
                            modifier = Modifier.weight(1f)
                        ) { Text("↺ RESET", fontFamily = FontFamily.Monospace, color = AccentCyan) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PFRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                  color: Color, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(110.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange, valueRange = range,
            modifier = Modifier.weight(1f).height(28.dp),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

@Composable
private fun EvoStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}
