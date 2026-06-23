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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivannafusion.AudioEngine
import com.ivannafusion.dsp.DSPState
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun EffectsScreen(audioEngine: AudioEngine, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("EQ", "COMP", "CONVOLVER", "SPATIAL")
    
    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary)) {
        IVANNAHeader(title = "EFECTOS", subtitle = "Procesamiento de señal") {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan) }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                IVANNAButton(
                    text = tab,
                    onClick = { selectedTab = index },
                    accent = if (selectedTab == index) AccentCyan else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (selectedTab) {
                0 -> EQPanel(audioEngine)
                1 -> CompressorPanel(audioEngine)
                2 -> ConvolverPanel(audioEngine)
                3 -> SpatialPanel(audioEngine)
                else -> {}            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun EQPanel(audioEngine: AudioEngine) {
    val bands = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    // CORREGIDO: antes 'gains'/'bypassed' eran 'remember { mutableStateOf(...) }'
    // local — Compose descarta ese estado al salir de la pantalla, por
    // eso los controles "se regresaban" al cambiar de ventana. Ahora se
    // leen de DSPState (StateFlow respaldado por SharedPreferences), que
    // persiste real entre navegaciones y reinicios de la app.
    val gains = (0 until 10).map { DSPState.eqGains[it].collectAsState().value }
    val bypassed by DSPState.eqBypass.collectAsState()

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ECUALIZADOR PARAMÉTRICO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BYPASS", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(end = 6.dp))
                IVANNAToggle(checked = bypassed, onCheckedChange = {
                    DSPState.setEqBypass(it)
                    audioEngine.eqSetBypass(it)
                })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "8 bandas reales (32–16K se mapean a las 8 bandas del motor PEQ activo)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            bands.forEachIndexed { index, label ->
                IVANNAKnob(
                    value = gains[index],
                    onValueChange = { newValue ->
                        DSPState.setEqGain(index, newValue)
                        // Las 10 bandas de UI se mapean a las 8 bandas reales del
                        // motor (clampBand en ivanna_native_lib_v2.cpp); las dos
                        // últimas (8K, 16K) comparten la banda 7 — limitación real
                        // del motor de 8 bandas, no del control de UI.
                        val realBand = index.coerceAtMost(7)
                        audioEngine.eqSetGain(realBand, newValue * 24f - 12f)
                    },
                    size = 64.dp,
                    range = 0f..1f,
                    label = label,
                    unit = "",
                    accentColor = AccentCyan,
                    enabled = !bypassed
                )
            }
        }
    }
}

@Composable
private fun CompressorPanel(audioEngine: AudioEngine) {
    val threshold by DSPState.compressorThreshold.collectAsState()
    val ratio by DSPState.compressorRatio.collectAsState()
    val attack by DSPState.compressorAttack.collectAsState()
    val release by DSPState.compressorRelease.collectAsState()
    val knee by DSPState.compressorKnee.collectAsState()
    val makeup by DSPState.compressorMakeup.collectAsState()
    val bypassed by DSPState.compressorBypass.collectAsState()

    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COMPRESOR DINÁMICO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BYPASS", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(end = 6.dp))
                IVANNAToggle(checked = bypassed, onCheckedChange = {
                    DSPState.setCompressorBypass(it)
                    audioEngine.compSetBypass(it)
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = threshold, onValueChange = { DSPState.setCompressorThreshold(it); audioEngine.compSetThreshold(it * -60f) }, size = 72.dp, label = "THRESH", unit = "dB", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = ratio, onValueChange = { DSPState.setCompressorRatio(it); audioEngine.compSetRatio(1f + it * 19f) }, size = 72.dp, label = "RATIO", unit = ":1", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = attack, onValueChange = { DSPState.setCompressorAttack(it); audioEngine.compSetAttack(it * 100f) }, size = 72.dp, label = "ATK", unit = "ms", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = release, onValueChange = { DSPState.setCompressorRelease(it); audioEngine.compSetRelease(it * 1000f) }, size = 72.dp, label = "REL", unit = "ms", accentColor = AccentMagenta, enabled = !bypassed)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = knee, onValueChange = { DSPState.setCompressorKnee(it); audioEngine.compSetKnee(it * 12f) }, size = 64.dp, label = "KNEE", unit = "dB", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = makeup, onValueChange = { DSPState.setCompressorMakeup(it); audioEngine.compSetMakeup(it * 24f) }, size = 64.dp, label = "MAKEUP", unit = "dB", accentColor = AccentMagenta, enabled = !bypassed)
        }
    }
}

@Composable
private fun ConvolverPanel(audioEngine: AudioEngine) {
    val reverbType by DSPState.reverbType.collectAsState()
    val decay by DSPState.reverbDecay.collectAsState()
    val preDelay by DSPState.reverbPreDelay.collectAsState()
    val damping by DSPState.reverbDamping.collectAsState()
    val diffusion by DSPState.reverbDiffusion.collectAsState()
    val earlyMix by DSPState.reverbEarlyMix.collectAsState()
    val mix by DSPState.reverbMix.collectAsState()
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("CONVOLVER ELITE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        
        // Selector de tipo de reverb
        Text("TIPO DE REVERB", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(bottom = 4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("HALL", "PLATE", "ROOM", "SPRING", "CHAMBER").forEach { type ->
                IVANNAButton(
                    text = type,
                    onClick = { 
                        DSPState.setReverbType(type)
                        audioEngine.convSetType(type)
                    },
                    accent = if (reverbType == type) AccentViolet else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Presets rápidos
        Text("PRESETS", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(bottom = 4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IVANNAButton(text = "SM ROOM", onClick = { audioEngine.convPresetSmallRoom() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
            IVANNAButton(text = "LG HALL", onClick = { audioEngine.convPresetLargeHall() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
            IVANNAButton(text = "PLATE", onClick = { audioEngine.convPresetPlate() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
            IVANNAButton(text = "SPRING", onClick = { audioEngine.convPresetSpring() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Controles principales
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = decay, onValueChange = { 
                DSPState.setReverbDecay(it)
                audioEngine.convSetDecay(it * 4.9f + 0.1f)
            }, size = 64.dp, label = "DECAY", unit = "s", accentColor = AccentViolet)
            
            IVANNAKnob(value = preDelay, onValueChange = { 
                DSPState.setReverbPreDelay(it)
                audioEngine.convSetPreDelay(it * 200f)
            }, size = 64.dp, label = "PRE-DLY", unit = "ms", accentColor = AccentViolet)
            
            IVANNAKnob(value = damping, onValueChange = { 
                DSPState.setReverbDamping(it)
                audioEngine.convSetDamping(it)
            }, size = 64.dp, label = "DAMP", unit = "", accentColor = AccentViolet)
            
            IVANNAKnob(value = diffusion, onValueChange = { 
                DSPState.setReverbDiffusion(it)
                audioEngine.convSetDiffusion(it)
            }, size = 64.dp, label = "DIFF", unit = "", accentColor = AccentViolet)
        }
        
        Spacer(Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = earlyMix, onValueChange = { 
                DSPState.setReverbEarlyMix(it)
                audioEngine.convSetEarlyMix(it)
            }, size = 64.dp, label = "EARLY", unit = "", accentColor = AccentViolet)
            
            IVANNAKnob(value = mix, onValueChange = { 
                DSPState.setReverbMix(it)
                audioEngine.convSetMix(it)
            }, size = 64.dp, label = "MIX", unit = "%", accentColor = AccentViolet)
        }
    }
}

@Composable
private fun SpatialPanel(audioEngine: AudioEngine) {
    val width by DSPState.widenerWidth.collectAsState()
    val depth by DSPState.widenerDepth.collectAsState()
    val diffusion by DSPState.widenerDiffusion.collectAsState()
    val delay by DSPState.widenerDelay.collectAsState()
    val modRate by DSPState.widenerModRate.collectAsState()
    val mix by DSPState.widenerMix.collectAsState()
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("DECORRELADOR ESTÉREO", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        
        // Presets rápidos
        Text("PRESETS", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(bottom = 4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IVANNAButton(text = "NATURAL", onClick = { audioEngine.decorPresetNatural() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
            IVANNAButton(text = "WIDE", onClick = { audioEngine.decorPresetWide() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
            IVANNAButton(text = "M→STEREO", onClick = { audioEngine.decorPresetMonoToStereo() }, accent = AccentEmerald, modifier = Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Controles principales
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = width, onValueChange = { 
                DSPState.setWidenerWidth(it)
                audioEngine.decorSetWidth(it * 2f)
            }, size = 64.dp, label = "WIDTH", unit = "", accentColor = AccentEmerald)
            
            IVANNAKnob(value = depth, onValueChange = { 
                DSPState.setWidenerDepth(it)
                audioEngine.decorSetDepth(it)
            }, size = 64.dp, label = "DEPTH", unit = "", accentColor = AccentEmerald)
            
            IVANNAKnob(value = diffusion, onValueChange = { 
                DSPState.setWidenerDiffusion(it)
                audioEngine.decorSetDiffusion(it)
            }, size = 64.dp, label = "DIFFUSION", unit = "", accentColor = AccentEmerald)
            
            IVANNAKnob(value = delay, onValueChange = { 
                DSPState.setWidenerDelay(it)
                audioEngine.decorSetDelay(it * 100f)
            }, size = 64.dp, label = "DELAY", unit = "ms", accentColor = AccentEmerald)
        }
        
        Spacer(Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = modRate, onValueChange = { 
                DSPState.setWidenerModRate(it)
                audioEngine.decorSetModRate(it * 5f)
            }, size = 64.dp, label = "MOD RATE", unit = "Hz", accentColor = AccentEmerald)
            
            IVANNAKnob(value = mix, onValueChange = { 
                DSPState.setWidenerMix(it)
                audioEngine.decorSetMix(it)
            }, size = 64.dp, label = "MIX", unit = "%", accentColor = AccentEmerald)
        }
    }
}

@Composable
private fun PendingEngineNotice(text: String) {
    Text(
        text = "⚠ $text",
        style = MaterialTheme.typography.labelSmall,
        color = AccentAmber,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}
