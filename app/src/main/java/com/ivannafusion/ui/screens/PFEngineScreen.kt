package com.ivannafusion.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.ivannafusion.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.ivannafusion.ui.components.IVANNAHeader
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.ivannafusion.ui.theme.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.delay
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

// ======================================================================
// CONSTANTES GLOBALES (definidas aquí para evitar errores)
// ======================================================================

val AMP_NAMES = listOf("CLEAN", "CRUNCH", "DRIVE", "METAL", "ACOUSTIC")

val PF_PRESETS = listOf(
    PFPreset("Clean Jazz", "🎸", 0, 0.3f, 0.2f, 1.2f, 0.1f, 0.4f, 0.0f, 0.0f, 0.0f, 0.0f, 0xFF44AAFF),
    PFPreset("Blues Crunch", "🔥", 1, 0.7f, 0.4f, 1.0f, 0.4f, 0.6f, 2.0f, 1.0f, 0.0f, 1.0f, 0xFFFF8C00),
    PFPreset("Rock Drive", "🎸", 2, 0.9f, 0.5f, 0.9f, 0.6f, 0.7f, 3.0f, 2.0f, 1.0f, 2.0f, 0xFFFF4444),
    PFPreset("Metal Core", "🤘", 3, 1.2f, 0.6f, 0.8f, 0.8f, 0.8f, 4.0f, 3.0f, 2.0f, 3.0f, 0xFFFFCC44),
    PFPreset("Acoustic", "🎵", 4, 0.2f, 0.1f, 1.4f, 0.0f, 0.3f, 0.0f, 0.0f, 0.0f, -1.0f, 0xFF888888)
)

data class PFPreset(
    val name: String,
    val emoji: String,
    val ampModel: Int,
    val drive: Float,
    val wet: Float,
    val alpha: Float,
    val delta: Float,
    val sigma: Float,
    val lowGain: Float,
    val midGain: Float,
    val highGain: Float,
    val presence: Float,
    val color: Long
)

// ======================================================================
// PANTALLA PF-ENGINE (DISEÑO DE ÉLITE)
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PFEngineScreen(audioEngine: AudioEngine, onBack: () -> Unit) {
    // Estados del DSP (se leen y escriben directamente)
    var ampModel by remember { mutableIntStateOf(DSPState.pfAmpModel) }
    var drive by remember { mutableFloatStateOf(DSPState.pfDrive) }
    var wet by remember { mutableFloatStateOf(DSPState.pfWet) }
    var alpha by remember { mutableFloatStateOf(DSPState.pfAlpha) }
    var delta by remember { mutableFloatStateOf(DSPState.pfDelta) }
    var sigma by remember { mutableFloatStateOf(DSPState.pfSigma) }
    var lowGain by remember { mutableFloatStateOf(DSPState.pfLowGain) }
    var midGain by remember { mutableFloatStateOf(DSPState.pfMidGain) }
    var highGain by remember { mutableFloatStateOf(DSPState.pfHighGain) }
    var presence by remember { mutableFloatStateOf(DSPState.pfPresence) }
    var evoActive by remember { mutableStateOf(false) }
    var barCount by remember { mutableIntStateOf(0) }
    var activePreset by remember { mutableStateOf<PFPreset?>(null) }
    val scope = rememberCoroutineScope()

    // Colores de amp
    val ampColors = listOf(
        Color(0xFF44AAFF), Color(0xFFFF8C00), Color(0xFFFF4444),
        Color(0xFFFFCC44), Color(0xFF888888)
    )

    // Efecto de evolución
    LaunchedEffect(evoActive) {
        if (evoActive) {
            while (evoActive) {
                audioEngine.pfEvoTick()
                barCount = audioEngine.pfGetBarCount()
                delay(500)
            }
        }
    }

    // Contenedor principal con fondo degradado y scroll
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0F), Color(0xFF14141A))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
            ) {
                Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan)
            }
            Text(
                text = "PF-ENGINE v3",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.W300,
                    letterSpacing = 2.sp,
                    fontSize = 18.sp,
                    color = Color.White.copy(0.9f)
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(44.dp))
        }

        Spacer(Modifier.height(12.dp))

        // ===== PRESETS RÁPIDOS =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "PRESETS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.5.sp,
                        color = AccentCyan.copy(0.8f),
                        fontSize = 10.sp
                    )
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PF_PRESETS) { preset ->
                        val isActive = activePreset?.name == preset.name
                        val c = Color(preset.color)
                        Button(
                            onClick = {
                                activePreset = preset
                                audioEngine.applyPFPreset(preset.name)
                                ampModel = preset.ampModel; drive = preset.drive; wet = preset.wet
                                alpha = preset.alpha; delta = preset.delta; sigma = preset.sigma
                                lowGain = preset.lowGain; midGain = preset.midGain
                                highGain = preset.highGain; presence = preset.presence
                                // Actualizar DSPState
                                DSPState.pfAmpModel = ampModel
                                DSPState.pfDrive = drive
                                DSPState.pfWet = wet
                                DSPState.pfAlpha = alpha
                                DSPState.pfDelta = delta
                                DSPState.pfSigma = sigma
                                DSPState.pfLowGain = lowGain
                                DSPState.pfMidGain = midGain
                                DSPState.pfHighGain = highGain
                                DSPState.pfPresence = presence
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) c.copy(alpha = 0.2f)
                                else Color(0x1AFFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(6.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(preset.emoji, fontSize = 18.sp)
                                Text(
                                    preset.name.split(" ").first(),
                                    color = if (isActive) c else TextSecondary,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.W500
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== AMP MODEL =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "AMP MODEL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.5.sp,
                        color = AccentAmber.copy(0.8f),
                        fontSize = 10.sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AMP_NAMES.forEachIndexed { i, name ->
                        Button(
                            onClick = {
                                ampModel = i
                                DSPState.pfAmpModel = i
                                audioEngine.pfSetAmp(i)
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ampModel == i) ampColors[i]
                                else Color(0x1AFFFFFF)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text(
                                name,
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (ampModel == i) Color.Black else ampColors[i]
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== PARÁMETROS (DRIVE, WET, ALPHA, etc.) =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "PARÁMETROS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.5.sp,
                        color = AccentAmber.copy(0.8f),
                        fontSize = 10.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                PFRow(
                    label = "Drive ${"%.2f".format(drive)}",
                    value = drive, range = 0f..2f, color = AccentMagenta,
                    onChange = {
                        drive = it
                        DSPState.pfDrive = it
                        audioEngine.pfSetParam("drive", it)
                    }
                )
                PFRow(
                    label = "Wet ${"%.0f".format(wet * 100)}%",
                    value = wet, range = 0f..1f, color = AccentCyan,
                    onChange = {
                        wet = it
                        DSPState.pfWet = it
                        audioEngine.pfSetParam("wet", it)
                    }
                )
                PFRow(
                    label = "α Tilt ${"%.2f".format(alpha)}",
                    value = alpha, range = 0.5f..2f, color = AccentViolet,
                    onChange = {
                        alpha = it
                        DSPState.pfAlpha = it
                        audioEngine.pfSetParam("alpha", it)
                    }
                )
                PFRow(
                    label = "δ Dist ${"%.2f".format(delta)}",
                    value = delta, range = 0f..1f, color = SignalHot,
                    onChange = {
                        delta = it
                        DSPState.pfDelta = it
                        audioEngine.pfSetParam("delta", it)
                    }
                )
                PFRow(
                    label = "σ Width ${"%.2f".format(sigma)}",
                    value = sigma, range = 0f..1f, color = AccentEmerald,
                    onChange = {
                        sigma = it
                        DSPState.pfSigma = it
                        audioEngine.pfSetParam("sigma", it)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== EQ 3 BANDAS + PRESENCE =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "EQ + PRESENCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.5.sp,
                        color = AccentAmber.copy(0.8f),
                        fontSize = 10.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                PFRow(
                    label = "Low ${"%.1f".format(lowGain)} dB",
                    value = lowGain + 12f, range = 0f..24f, color = AccentEmerald,
                    onChange = {
                        lowGain = it - 12f
                        DSPState.pfLowGain = lowGain
                        audioEngine.pfSetParam("low", lowGain)
                    }
                )
                PFRow(
                    label = "Mid ${"%.1f".format(midGain)} dB",
                    value = midGain + 12f, range = 0f..24f, color = AccentCyan,
                    onChange = {
                        midGain = it - 12f
                        DSPState.pfMidGain = midGain
                        audioEngine.pfSetParam("mid", midGain)
                    }
                )
                PFRow(
                    label = "High ${"%.1f".format(highGain)} dB",
                    value = highGain + 12f, range = 0f..24f, color = AccentAmber,
                    onChange = {
                        highGain = it - 12f
                        DSPState.pfHighGain = highGain
                        audioEngine.pfSetParam("high", highGain)
                    }
                )
                PFRow(
                    label = "Presence ${"%.1f".format(presence)}",
                    value = presence + 6f, range = 0f..12f, color = AccentMagenta,
                    onChange = {
                        presence = it - 6f
                        DSPState.pfPresence = presence
                        audioEngine.pfSetParam("presence", presence)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== EVOLUTION CURVE =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "EVOLUTION CURVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.5.sp,
                        color = AccentViolet.copy(0.8f),
                        fontSize = 10.sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    EvoStat("BAR", "$barCount", AccentViolet)
                    EvoStat(
                        "FASE",
                        when {
                            barCount < 16 -> "BASE"
                            barCount < 32 -> "BUILD"
                            barCount < 48 -> "PEAK"
                            else -> "DECAY"
                        },
                        when {
                            barCount < 16 -> TextSecondary
                            barCount < 32 -> AccentCyan
                            barCount < 48 -> SignalHot
                            else -> AccentViolet
                        }
                    )
                    EvoStat(
                        "STATUS",
                        if (evoActive) "▶ ON" else "⏹ OFF",
                        if (evoActive) AccentEmerald else TextSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            evoActive = !evoActive
                            if (!evoActive) audioEngine.pfEvoStop()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (evoActive) SignalHot else AccentEmerald
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (evoActive) "⏹ STOP" else "▶ START",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            barCount = 0
                            audioEngine.pfEvoReset()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "↺ RESET",
                            fontFamily = FontFamily.Monospace,
                            color = AccentCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ======================================================================
// COMPONENTES REUTILIZABLES DE DISEÑO DE ÉLITE
// ======================================================================

@Composable
private fun PFRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(100.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}

@Composable
private fun EvoStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Text(
            value,
            color = color,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
