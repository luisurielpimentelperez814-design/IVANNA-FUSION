package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
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
                    text = tab, onClick = { selectedTab = index },
                    modifier = Modifier.weight(1f),
                    accent = if (selectedTab == index) AccentCyan else TextSecondary
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

// ─── EQ ────────────────────────────────────────────────────────────────────────
@Composable
private fun EQPanel(audioEngine: AudioEngine) {
    val labels = listOf("32","64","125","250","500","1K","2K","4K","8K","16K")
    var gains     by remember { mutableStateOf(DSPState.eqGains.toList()) }
    var bypassed  by remember { mutableStateOf(DSPState.eqBypassed) }

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("ECUALIZADOR 10 BANDAS", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BYPASS", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary, modifier = Modifier.padding(end = 6.dp))
                IVANNAToggle(checked = bypassed, onCheckedChange = {
                    bypassed = it; DSPState.eqBypassed = it; audioEngine.eqSetBypass(it)
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEachIndexed { i, label ->
                IVANNAKnob(
                    value     = gains[i],
                    onValueChange = { v ->
                        gains = gains.toMutableList().also { it[i] = v }
                        DSPState.eqGains[i] = v
                        DSPState.saveEQ()
                        audioEngine.eqSetGain(i.coerceAtMost(7), v * 24f - 12f)
                    },
                    size      = 60.dp,
                    range     = 0f..1f,
                    label     = label,
                    unit      = "dB",
                    accentColor = AccentCyan,
                    enabled   = !bypassed
                )
            }
        }
    }
}

// ─── Compressor ────────────────────────────────────────────────────────────────
@Composable
private fun CompressorPanel(audioEngine: AudioEngine) {
    var threshold by remember { mutableFloatStateOf(DSPState.compThreshold) }
    var ratio     by remember { mutableFloatStateOf(DSPState.compRatio) }
    var attack    by remember { mutableFloatStateOf(DSPState.compAttack) }
    var release   by remember { mutableFloatStateOf(DSPState.compRelease) }
    var knee      by remember { mutableFloatStateOf(DSPState.compKnee) }
    var makeup    by remember { mutableFloatStateOf(DSPState.compMakeup) }
    var bypassed  by remember { mutableStateOf(DSPState.compBypassed) }

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("COMPRESOR DINÁMICO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BYPASS", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary, modifier = Modifier.padding(end = 6.dp))
                IVANNAToggle(checked = bypassed, onCheckedChange = {
                    bypassed = it; DSPState.compBypassed = it; audioEngine.compSetBypass(it)
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = threshold, size = 72.dp, label = "THRESH", unit = "dB",
                accentColor = AccentMagenta, enabled = !bypassed,
                onValueChange = { threshold = it; DSPState.compThreshold = it
                    DSPState.saveCompressor(); audioEngine.compSetThreshold(it * -60f) })
            IVANNAKnob(value = ratio, size = 72.dp, label = "RATIO", unit = ":1",
                accentColor = AccentMagenta, enabled = !bypassed,
                onValueChange = { ratio = it; DSPState.compRatio = it
                    DSPState.saveCompressor(); audioEngine.compSetRatio(1f + it * 19f) })
            IVANNAKnob(value = attack, size = 72.dp, label = "ATK", unit = "ms",
                accentColor = AccentMagenta, enabled = !bypassed,
                onValueChange = { attack = it; DSPState.compAttack = it
                    DSPState.saveCompressor(); audioEngine.compSetAttack(it * 100f) })
            IVANNAKnob(value = release, size = 72.dp, label = "REL", unit = "ms",
                accentColor = AccentMagenta, enabled = !bypassed,
                onValueChange = { release = it; DSPState.compRelease = it
                    DSPState.saveCompressor(); audioEngine.compSetRelease(it * 1000f) })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = knee, size = 64.dp, label = "KNEE", unit = "dB",
                accentColor = AccentMagenta, enabled = !bypassed,
                onValueChange = { knee = it; DSPState.compKnee = it; audioEngine.compSetKnee(it * 12f) })
            IVANNAKnob(value = makeup, size = 64.dp, label = "MAKEUP", unit = "dB",
                accentColor = AccentMagenta, enabled = !bypassed,
                onValueChange = { makeup = it; DSPState.compMakeup = it; audioEngine.compSetMakeup(it * 24f) })
        }
    }
}

// ─── Convolver ─────────────────────────────────────────────────────────────────
@Composable
private fun ConvolverPanel(audioEngine: AudioEngine) {
    var reverbType by remember { mutableStateOf(DSPState.convType) }
    var decay      by remember { mutableFloatStateOf(DSPState.convDecay) }
    var preDelay   by remember { mutableFloatStateOf(DSPState.convPreDelay) }
    var damping    by remember { mutableFloatStateOf(DSPState.convDamping) }
    var diffusion  by remember { mutableFloatStateOf(DSPState.convDiffusion) }
    var earlyMix   by remember { mutableFloatStateOf(DSPState.convEarlyMix) }
    var mix        by remember { mutableFloatStateOf(DSPState.convMix) }

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
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IVANNAButton("SM ROOM",  { audioEngine.convPresetSmallRoom() }, Modifier.weight(1f), AccentEmerald)
            IVANNAButton("LG HALL",  { audioEngine.convPresetLargeHall() }, Modifier.weight(1f), AccentEmerald)
            IVANNAButton("PLATE",    { audioEngine.convPresetPlate() },      Modifier.weight(1f), AccentEmerald)
            IVANNAButton("SPRING",   { audioEngine.convPresetSpring() },     Modifier.weight(1f), AccentEmerald)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = decay, size = 64.dp, label = "DECAY", unit = "s",
                accentColor = AccentViolet,
                onValueChange = { decay = it; DSPState.convDecay = it; audioEngine.convSetDecay(it * 4.9f + 0.1f) })
            IVANNAKnob(value = preDelay, size = 64.dp, label = "PRE-DLY", unit = "ms",
                accentColor = AccentViolet,
                onValueChange = { preDelay = it; DSPState.convPreDelay = it; audioEngine.convSetPreDelay(it * 200f) })
            IVANNAKnob(value = damping, size = 64.dp, label = "DAMP", unit = "",
                accentColor = AccentViolet,
                onValueChange = { damping = it; DSPState.convDamping = it; audioEngine.convSetDamping(it) })
            IVANNAKnob(value = diffusion, size = 64.dp, label = "DIFF", unit = "",
                accentColor = AccentViolet,
                onValueChange = { diffusion = it; DSPState.convDiffusion = it; audioEngine.convSetDiffusion(it) })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = earlyMix, size = 64.dp, label = "EARLY", unit = "",
                accentColor = AccentViolet,
                onValueChange = { earlyMix = it; DSPState.convEarlyMix = it; audioEngine.convSetEarlyMix(it) })
            IVANNAKnob(value = mix, size = 64.dp, label = "MIX", unit = "%",
                accentColor = AccentViolet,
                onValueChange = { mix = it; DSPState.convMix = it; audioEngine.convSetMix(it) })
        }
    }
}

// ─── Spatial ───────────────────────────────────────────────────────────────────
@Composable
private fun SpatialPanel(audioEngine: AudioEngine) {
    var width     by remember { mutableFloatStateOf(DSPState.spatWidth) }
    var depth     by remember { mutableFloatStateOf(DSPState.spatDepth) }
    var diffusion by remember { mutableFloatStateOf(DSPState.spatDiffusion) }
    var delay     by remember { mutableFloatStateOf(DSPState.spatDelay) }
    var modRate   by remember { mutableFloatStateOf(DSPState.spatModRate) }
    var mix       by remember { mutableFloatStateOf(DSPState.spatMix) }

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("DECORRELADOR ESTÉREO", style = MaterialTheme.typography.labelSmall,
            color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IVANNAButton("NATURAL",   { audioEngine.decorPresetNatural() },     Modifier.weight(1f), AccentEmerald)
            IVANNAButton("WIDE",      { audioEngine.decorPresetWide() },         Modifier.weight(1f), AccentEmerald)
            IVANNAButton("M→STEREO", { audioEngine.decorPresetMonoToStereo() }, Modifier.weight(1f), AccentEmerald)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = width, size = 64.dp, label = "WIDTH", unit = "",
                accentColor = AccentEmerald,
                onValueChange = { width = it; DSPState.spatWidth = it; audioEngine.decorSetWidth(it * 2f) })
            IVANNAKnob(value = depth, size = 64.dp, label = "DEPTH", unit = "",
                accentColor = AccentEmerald,
                onValueChange = { depth = it; DSPState.spatDepth = it; audioEngine.decorSetDepth(it) })
            IVANNAKnob(value = diffusion, size = 64.dp, label = "DIFF", unit = "",
                accentColor = AccentEmerald,
                onValueChange = { diffusion = it; DSPState.spatDiffusion = it; audioEngine.decorSetDiffusion(it) })
            IVANNAKnob(value = delay, size = 64.dp, label = "DELAY", unit = "ms",
                accentColor = AccentEmerald,
                onValueChange = { delay = it; DSPState.spatDelay = it; audioEngine.decorSetDelay(it * 100f) })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = modRate, size = 64.dp, label = "MOD RATE", unit = "Hz",
                accentColor = AccentEmerald,
                onValueChange = { modRate = it; DSPState.spatModRate = it; audioEngine.decorSetModRate(it * 5f) })
            IVANNAKnob(value = mix, size = 64.dp, label = "MIX", unit = "%",
                accentColor = AccentEmerald,
                onValueChange = { mix = it; DSPState.spatMix = it; audioEngine.decorSetMix(it) })
        }
    }
}
