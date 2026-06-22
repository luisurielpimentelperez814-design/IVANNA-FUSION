/*
 * IVANNA-FUSION TRASCENDENTAL v2.0
 * PresetsScreen.kt — Selector de presets PF-ENGINE con parámetros DSP
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */
package com.ivannafusion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Modelo de datos de preset ────────────────────────────────────────────────
data class PFPreset(
    val name: String,
    val displayName: String,
    val emoji: String,
    val ampModel: Int,          // 0=Marshall 1=Fender 2=Vox 3=Rock70s 4=Bypass
    val alpha: Float,
    val beta: Float,
    val gamma: Float,
    val delta: Float,
    val sigma: Float,
    val drive: Float,
    val wet: Float,
    val lowGain: Float,
    val midGain: Float,
    val highGain: Float,
    val midFreq: Float,
    val presence: Float,
    val sag: Float,
    val bias: Float,
    val description: String,
    val color: Color
)

// ─── Catálogo de presets (portados del PF-ENGINE-PRO-MAX-NEXT) ────────────────
val PRESETS = listOf(
    PFPreset("clean_studio", "Clean Studio", "🎙️", 1,
        alpha=0.95f, beta=0.15f, gamma=0.40f, delta=0.10f, sigma=0.70f,
        drive=0.80f, wet=0.60f, lowGain=2.0f, midGain=0.5f, highGain=1.5f,
        midFreq=500f, presence=1.5f, sag=0.05f, bias=0.55f,
        "Fender limpio. Ideal para grabación vocal y guitarras cristalinas.",
        Color(0xFF44AAFF)),

    PFPreset("marshall_crunch", "Marshall Crunch", "🎸", 0,
        alpha=1.10f, beta=0.55f, gamma=0.60f, delta=0.75f, sigma=0.40f,
        drive=3.20f, wet=0.85f, lowGain=3.0f, midGain=-1.5f, highGain=4.0f,
        midFreq=700f, presence=5.0f, sag=0.25f, bias=0.45f,
        "Stack Marshall clásico. Crunch agresivo con presencia brutal.",
        Color(0xFFFF4444)),

    PFPreset("vox_sparkle", "Vox Sparkle", "✨", 2,
        alpha=1.00f, beta=0.40f, gamma=0.50f, delta=0.35f, sigma=0.55f,
        drive=1.80f, wet=0.70f, lowGain=0.5f, midGain=3.5f, highGain=2.0f,
        midFreq=1200f, presence=3.0f, sag=0.12f, bias=0.50f,
        "AC30 estilo Vox. Medios brillantes y presencia chispeante.",
        Color(0xFFFFCC44)),

    PFPreset("70s_rock", "70s Rock", "🎤", 3,
        alpha=1.05f, beta=0.50f, gamma=0.65f, delta=0.60f, sigma=0.50f,
        drive=2.80f, wet=0.80f, lowGain=4.0f, midGain=1.0f, highGain=3.0f,
        midFreq=900f, presence=4.0f, sag=0.20f, bias=0.48f,
        "Full-stack 70s (Rush, Grand Funk). Cuerpo y ataque imparable.",
        Color(0xFFFF8C00)),

    PFPreset("psychedelic", "Psychedelic", "🌀", 3,
        alpha=1.00f, beta=0.55f, gamma=0.60f, delta=0.50f, sigma=0.70f,
        drive=2.20f, wet=0.75f, lowGain=2.0f, midGain=2.5f, highGain=3.5f,
        midFreq=1400f, presence=5.0f, sag=0.15f, bias=0.50f,
        "Textura sicodélica con amplios harmónicos. Floyd / Hendrix.",
        Color(0xFFCC44FF)),

    PFPreset("flat", "Flat", "⚖️", 4,
        alpha=1.00f, beta=0.00f, gamma=0.50f, delta=0.00f, sigma=0.50f,
        drive=1.00f, wet=0.00f, lowGain=0.0f, midGain=0.0f, highGain=0.0f,
        midFreq=800f, presence=0.0f, sag=0.00f, bias=0.50f,
        "Bypass total. Señal pura, sin coloración.",
        Color(0xFF888888))
)

val AMP_NAMES = listOf("Marshall", "Fender", "Vox", "70s Rock", "Bypass")

// ─── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun PresetsScreen(navController: NavController, audioEngine: AudioEngine) {
    var activePreset by remember { mutableStateOf<PFPreset?>(null) }
    var feedbackMsg  by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060610))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎛️ PRESETS DSP", color = Color(0xFF44AAFF), fontSize = 18.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text("PF-ENGINE v3.0", color = Color.Gray, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
        }
        Text("© 2025 GORE TNS — Luis Uriel Pimentel Pérez",
            color = Color.DarkGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

        Spacer(Modifier.height(14.dp))

        // Preset grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.heightIn(max = 560.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(PRESETS) { preset ->
                PresetCard(
                    preset = preset,
                    isActive = activePreset?.name == preset.name,
                    onClick = {
                        activePreset = preset
                        AudioEngine.applyPFPreset(preset)
                        scope.launch {
                            feedbackMsg = "✓ ${preset.displayName} aplicado"
                            delay(2200)
                            feedbackMsg = ""
                        }
                    }
                )
            }
        }

        // Feedback
        if (feedbackMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(feedbackMsg, color = Color(0xFF44FF88), fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A220A), RoundedCornerShape(8.dp))
                    .padding(10.dp))
        }

        // Detail panel del preset activo
        activePreset?.let { p ->
            Spacer(Modifier.height(14.dp))
            PresetDetailPanel(p)
        }

        // Nav
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { navController.navigate("monitor") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF44AAFF))) {
                Text("MONITOR", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = { navController.navigate("pfengine") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8C00))) {
                Text("PF-ENGINE", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun PresetCard(preset: PFPreset, isActive: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        if (isActive) preset.color else Color.Transparent,
        animationSpec = tween(300), label = "border"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                preset.color.copy(alpha = 0.15f)
            else Color(0xFF0F0F22)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(preset.emoji + " " + preset.displayName,
                color = if (isActive) preset.color else Color.White,
                fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(AMP_NAMES.getOrElse(preset.ampModel) { "N/A" },
                color = preset.color.copy(alpha = 0.8f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(2.dp))
            Text("drive: %.1f  wet: %.0f%%".format(preset.drive, preset.wet * 100),
                color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PresetDetailPanel(p: PFPreset) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("${p.emoji} ${p.displayName}", color = p.color, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(p.description, color = Color.Gray, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            // PF params table
            val params = listOf(
                "alpha" to p.alpha, "beta" to p.beta, "gamma" to p.gamma,
                "delta" to p.delta, "sigma" to p.sigma, "drive" to p.drive,
                "wet" to p.wet, "sag" to p.sag, "bias" to p.bias
            )
            val eq = listOf(
                "low_gain" to p.lowGain, "mid_gain" to p.midGain,
                "high_gain" to p.highGain, "presence" to p.presence
            )
            Text("PF PARAMS", color = Color.DarkGray, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
            params.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, color = Color.Gray, fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace)
                            Text("%.2f".format(v), color = p.color, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("EQ (dB)", color = Color.DarkGray, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
            Row(Modifier.fillMaxWidth()) {
                eq.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k.replace("_gain",""), color = Color.Gray, fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace)
                        Text("%+.1f".format(v),
                            color = if (v >= 0) Color(0xFF44FF88) else Color(0xFFFF5555),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("mid_freq: %.0f Hz  amp: ${AMP_NAMES.getOrElse(p.ampModel){"?"}}".format(p.midFreq),
                color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
