package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivannafusion.AudioEngine
import com.ivannafusion.DSPState
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun EffectsScreen(audioEngine: AudioEngine, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("EQ", "COMP", "CONVOLVER", "SPATIAL")

    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary)) {
        IVANNAHeader(title = "EFECTOS", subtitle = "Procesamiento de señal") {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                IVANNAButton(
                    text     = tab,
                    onClick  = { selectedTab = index },
                    modifier = Modifier.weight(1f),
                    accent   = if (selectedTab == index) AccentCyan else TextSecondary
                )
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (selectedTab) {
                0 -> EQPanel(audioEngine)
                1 -> CompressorPanel(audioEngine)
                2 -> ConvolverPanel(audioEngine)
                3 -> SpatialPanel(audioEngine)
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ─── EQ  ±18 dB por banda ─────────────────────────────────────────────────────
@Composable
private fun EQPanel(audioEngine: AudioEngine) {
    val freqLabels = listOf("32","64","125","250","500","1K","2K","4K","8K","16K")
    // DSPState guarda 0..1 donde 0.5 = 0 dB (rango anterior ±12 dB).
    // Nuevo rango ±18 dB: convertimos la semilla inicial y guardamos con el nuevo factor.
    val gains = remember {
        mutableStateListOf(*Array(10) { i ->
            (DSPState.eqGains[i] * 24f - 12f).coerceIn(-18f, 18f)
        })
    }
    var bypassed by remember { mutableStateOf(DSPState.eqBypassed) }

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("ECUALIZADOR 10 BANDAS — ±18 dB",
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BYPASS", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary, modifier = Modifier.padding(end = 6.dp))
                IVANNAToggle(checked = bypassed, onCheckedChange = {
                    bypassed = it
                    DSPState.eqBypassed = it
                    audioEngine.eqSetBypass(0, it)
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        freqLabels.forEachIndexed { i, label ->
            IVANNASlider(
                value       = gains[i],
                onValueChange = { v ->
                    gains[i]          = v
                    DSPState.eqGains[i] = (v + 18f) / 36f  // normalizar al nuevo ±18
                    DSPState.saveEQ()
                    audioEngine.eqSetGain(i.coerceAtMost(7), v)
                },
                range       = -18f..18f,
                label       = label,
                labelWidth  = 36.dp,
                valueText   = "%+.1f dB".format(gains[i]),
                valueWidth  = 72.dp,
                accentColor = AccentCyan,
                enabled     = !bypassed,
                compact     = true
            )
        }
        Spacer(Modifier.height(4.dp))
        IVANNAButton("RESET FLAT", onClick = {
            for (i in 0..9) {
                gains[i]           = 0f
                DSPState.eqGains[i] = 0.5f
                audioEngine.eqSetGain(i.coerceAtMost(7), 0f)
            }
            DSPState.saveEQ()
        }, modifier = Modifier.fillMaxWidth(), accent = AccentCyan)
    }
}

// ─── Compresor  rangos extendidos ─────────────────────────────────────────────
@Composable
private fun CompressorPanel(audioEngine: AudioEngine) {
    // Convertir semillas 0..1 de DSPState a valores reales
    var threshold by remember { mutableFloatStateOf(-(DSPState.compThreshold * 60f)) }  // 0..-60 dB
    var ratio     by remember { mutableFloatStateOf(1f + DSPState.compRatio * 19f) }     // 1..20:1
    var attack    by remember { mutableFloatStateOf(DSPState.compAttack * 200f) }         // 0..200 ms
    var release   by remember { mutableFloatStateOf(DSPState.compRelease * 3000f) }       // 0..3000 ms
    var knee      by remember { mutableFloatStateOf(DSPState.compKnee * 24f) }            // 0..24 dB
    var makeup    by remember { mutableFloatStateOf(DSPState.compMakeup * 36f) }          // 0..36 dB
    var bypassed  by remember { mutableStateOf(DSPState.compBypassed) }

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("COMPRESOR DINÁMICO", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BYPASS", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary, modifier = Modifier.padding(end = 6.dp))
                IVANNAToggle(checked = bypassed, onCheckedChange = {
                    bypassed = it; DSPState.compBypassed = it; audioEngine.compSetBypass(it)
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        IVANNASlider(
            value = threshold, range = -60f..0f,
            label = "THRESH", valueText = "%.1f dB".format(threshold),
            accentColor = AccentMagenta, enabled = !bypassed,
            onValueChange = { threshold = it
                DSPState.compThreshold = (-it / 60f).coerceIn(0f, 1f)
                DSPState.saveCompressor(); audioEngine.compSetThreshold(it) }
        )
        IVANNASlider(
            value = ratio, range = 1f..20f,
            label = "RATIO", valueText = "%.1f:1".format(ratio),
            accentColor = AccentMagenta, enabled = !bypassed,
            onValueChange = { ratio = it
                DSPState.compRatio = ((it - 1f) / 19f).coerceIn(0f, 1f)
                DSPState.saveCompressor(); audioEngine.compSetRatio(it) }
        )
        IVANNASlider(
            value = attack, range = 0.1f..200f,
            label = "ATK", valueText = "%.1f ms".format(attack),
            accentColor = AccentMagenta, enabled = !bypassed,
            onValueChange = { attack = it
                DSPState.compAttack = (it / 200f).coerceIn(0f, 1f)
                DSPState.saveCompressor(); audioEngine.compSetAttack(it) }
        )
        IVANNASlider(
            value = release, range = 10f..3000f,
            label = "REL", valueText = "%.0f ms".format(release),
            accentColor = AccentMagenta, enabled = !bypassed,
            onValueChange = { release = it
                DSPState.compRelease = (it / 3000f).coerceIn(0f, 1f)
                DSPState.saveCompressor(); audioEngine.compSetRelease(it) }
        )
        IVANNASlider(
            value = knee, range = 0f..24f,
            label = "KNEE", valueText = "%.1f dB".format(knee),
            accentColor = AccentMagenta, enabled = !bypassed,
            onValueChange = { knee = it
                DSPState.compKnee = (it / 24f).coerceIn(0f, 1f)
                audioEngine.compSetKnee(it) }
        )
        IVANNASlider(
            value = makeup, range = 0f..36f,
            label = "MAKEUP", valueText = "%+.1f dB".format(makeup),
            accentColor = AccentMagenta, enabled = !bypassed,
            onValueChange = { makeup = it
                DSPState.compMakeup = (it / 36f).coerceIn(0f, 1f)
                audioEngine.compSetMakeup(it) }
        )
    }
}

// ─── Convolver ────────────────────────────────────────────────────────────────
@Composable
private fun ConvolverPanel(audioEngine: AudioEngine) {
    var reverbType by remember { mutableStateOf<String>(DSPState.convType) }
    var decay     by remember { mutableFloatStateOf(DSPState.convDecay * 9.9f + 0.1f) }    // 0.1..10 s
    var preDelay  by remember { mutableFloatStateOf(DSPState.convPreDelay * 500f) }          // 0..500 ms
    var damping   by remember { mutableFloatStateOf(DSPState.convDamping) }
    var diffusion by remember { mutableFloatStateOf(DSPState.convDiffusion) }
    var earlyMix  by remember { mutableFloatStateOf(DSPState.convEarlyMix) }
    var mix       by remember { mutableFloatStateOf(DSPState.convMix * 100f) }               // 0..100 %

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("CONVOLVER ELITE", style = MaterialTheme.typography.labelSmall,
            color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("HALL","PLATE","ROOM","SPRING","CHAMBER").forEach { type ->
                IVANNAButton(text = type, onClick = {
                    reverbType = type; DSPState.convType = type; audioEngine.convSetType(type)
                }, modifier = Modifier.weight(1f),
                   accent = if (reverbType == type) AccentViolet else TextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IVANNAButton("SM ROOM", { audioEngine.convPresetSmallRoom() }, Modifier.weight(1f), AccentEmerald)
            IVANNAButton("LG HALL", { audioEngine.convPresetLargeHall() }, Modifier.weight(1f), AccentEmerald)
            IVANNAButton("PLATE",   { audioEngine.convPresetPlate() },     Modifier.weight(1f), AccentEmerald)
            IVANNAButton("SPRING",  { audioEngine.convPresetSpring() },    Modifier.weight(1f), AccentEmerald)
        }
        Spacer(Modifier.height(12.dp))

        IVANNASlider(
            value = decay, range = 0.1f..10f,
            label = "DECAY", valueText = "%.2f s".format(decay),
            accentColor = AccentViolet,
            onValueChange = { decay = it
                DSPState.convDecay = ((it - 0.1f) / 9.9f).coerceIn(0f, 1f)
                audioEngine.convSetDecay(it) }
        )
        IVANNASlider(
            value = preDelay, range = 0f..500f,
            label = "PRE-DLY", valueText = "%.0f ms".format(preDelay),
            accentColor = AccentViolet,
            onValueChange = { preDelay = it
                DSPState.convPreDelay = (it / 500f).coerceIn(0f, 1f)
                audioEngine.convSetPreDelay(it) }
        )
        IVANNASlider(
            value = damping, range = 0f..1f,
            label = "DAMP", valueText = "%.2f".format(damping),
            accentColor = AccentViolet,
            onValueChange = { damping = it; DSPState.convDamping = it; audioEngine.convSetDamping(it) }
        )
        IVANNASlider(
            value = diffusion, range = 0f..1f,
            label = "DIFF", valueText = "%.2f".format(diffusion),
            accentColor = AccentViolet,
            onValueChange = { diffusion = it; DSPState.convDiffusion = it; audioEngine.convSetDiffusion(it) }
        )
        IVANNASlider(
            value = earlyMix, range = 0f..1f,
            label = "EARLY", valueText = "%.2f".format(earlyMix),
            accentColor = AccentViolet,
            onValueChange = { earlyMix = it; DSPState.convEarlyMix = it; audioEngine.convSetEarlyMix(it) }
        )
        IVANNASlider(
            value = mix, range = 0f..100f,
            label = "MIX", valueText = "%.0f %%".format(mix),
            accentColor = AccentViolet,
            onValueChange = { mix = it
                DSPState.convMix = (it / 100f).coerceIn(0f, 1f)
                audioEngine.convSetMix(it / 100f) }
        )
    }
}

// ─── Spatial ──────────────────────────────────────────────────────────────────
@Composable
private fun SpatialPanel(audioEngine: AudioEngine) {
    var width     by remember { mutableFloatStateOf(DSPState.spatWidth * 5f) }       // 0..5
    var depth     by remember { mutableFloatStateOf(DSPState.spatDepth) }
    var diffusion by remember { mutableFloatStateOf(DSPState.spatDiffusion) }
    var delay     by remember { mutableFloatStateOf(DSPState.spatDelay * 200f) }      // 0..200 ms
    var modRate   by remember { mutableFloatStateOf(DSPState.spatModRate * 15f) }     // 0..15 Hz
    var mix       by remember { mutableFloatStateOf(DSPState.spatMix * 100f) }        // 0..100 %

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("DECORRELADOR ESTÉREO", style = MaterialTheme.typography.labelSmall,
            color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IVANNAButton("NATURAL",   { audioEngine.decorPresetNatural() },     Modifier.weight(1f), AccentEmerald)
            IVANNAButton("WIDE",      { audioEngine.decorPresetWide() },         Modifier.weight(1f), AccentEmerald)
            IVANNAButton("M→STEREO", { audioEngine.decorPresetMonoToStereo() }, Modifier.weight(1f), AccentEmerald)
        }
        Spacer(Modifier.height(12.dp))

        IVANNASlider(
            value = width, range = 0f..5f,
            label = "WIDTH", valueText = "%.2f".format(width),
            accentColor = AccentEmerald,
            onValueChange = { width = it
                DSPState.spatWidth = (it / 5f).coerceIn(0f, 1f)
                audioEngine.decorSetWidth(it) }
        )
        IVANNASlider(
            value = depth, range = 0f..1f,
            label = "DEPTH", valueText = "%.2f".format(depth),
            accentColor = AccentEmerald,
            onValueChange = { depth = it; DSPState.spatDepth = it; audioEngine.decorSetDepth(it) }
        )
        IVANNASlider(
            value = diffusion, range = 0f..1f,
            label = "DIFF", valueText = "%.2f".format(diffusion),
            accentColor = AccentEmerald,
            onValueChange = { diffusion = it; DSPState.spatDiffusion = it; audioEngine.decorSetDiffusion(it) }
        )
        IVANNASlider(
            value = delay, range = 0f..200f,
            label = "DELAY", valueText = "%.0f ms".format(delay),
            accentColor = AccentEmerald,
            onValueChange = { delay = it
                DSPState.spatDelay = (it / 200f).coerceIn(0f, 1f)
                audioEngine.decorSetDelay(it) }
        )
        IVANNASlider(
            value = modRate, range = 0f..15f,
            label = "MOD RATE", valueText = "%.1f Hz".format(modRate),
            accentColor = AccentEmerald,
            onValueChange = { modRate = it
                DSPState.spatModRate = (it / 15f).coerceIn(0f, 1f)
                audioEngine.decorSetModRate(it) }
        )
        IVANNASlider(
            value = mix, range = 0f..100f,
            label = "MIX", valueText = "%.0f %%".format(mix),
            accentColor = AccentEmerald,
            onValueChange = { mix = it
                DSPState.spatMix = (it / 100f).coerceIn(0f, 1f)
                audioEngine.decorSetMix(it / 100f) }
        )
    }
}
