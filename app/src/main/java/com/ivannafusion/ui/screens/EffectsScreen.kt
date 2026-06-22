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
    var gains by remember { mutableStateOf(List(10) { 0.5f }) }
    var bypassed by remember { mutableStateOf(false) }

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
                    bypassed = it
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
                        gains = gains.toMutableList().also { it[index] = newValue }
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
    var threshold by remember { mutableStateOf(0.5f) }
    var ratio by remember { mutableStateOf(0.2f) }
    var attack by remember { mutableStateOf(0.1f) }
    var release by remember { mutableStateOf(0.3f) }
    var knee by remember { mutableStateOf(0.3f) }
    var makeup by remember { mutableStateOf(0.0f) }
    var bypassed by remember { mutableStateOf(false) }

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
                    bypassed = it
                    audioEngine.compSetBypass(it)
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = threshold, onValueChange = { threshold = it; audioEngine.compSetThreshold(it * -60f) }, size = 72.dp, label = "THRESH", unit = "dB", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = ratio, onValueChange = { ratio = it; audioEngine.compSetRatio(1f + it * 19f) }, size = 72.dp, label = "RATIO", unit = ":1", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = attack, onValueChange = { attack = it; audioEngine.compSetAttack(it * 100f) }, size = 72.dp, label = "ATK", unit = "ms", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = release, onValueChange = { release = it; audioEngine.compSetRelease(it * 1000f) }, size = 72.dp, label = "REL", unit = "ms", accentColor = AccentMagenta, enabled = !bypassed)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = knee, onValueChange = { knee = it; audioEngine.compSetKnee(it * 12f) }, size = 64.dp, label = "KNEE", unit = "dB", accentColor = AccentMagenta, enabled = !bypassed)
            IVANNAKnob(value = makeup, onValueChange = { makeup = it; audioEngine.compSetMakeup(it * 24f) }, size = 64.dp, label = "MAKEUP", unit = "dB", accentColor = AccentMagenta, enabled = !bypassed)
        }
    }
}

@Composable
private fun ConvolverPanel(audioEngine: AudioEngine) {
    var enabled by remember { mutableStateOf(false) }
    var mix by remember { mutableStateOf(0.5f) }
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CONVOLVER", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            IVANNAToggle(checked = enabled, onCheckedChange = { enabled = it; audioEngine.convolverSetEnabled(it) })
        }
        PendingEngineNotice("Sin motor de convolución implementado todavía — los controles guardan el valor pero no procesan audio.")
        
        Spacer(Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = mix, onValueChange = { mix = it; audioEngine.convolverSetMix(it) }, label = "MIX", unit = "%", accentColor = AccentViolet, enabled = false)
        }
    }
}

@Composable
private fun SpatialPanel(audioEngine: AudioEngine) {
    var width by remember { mutableStateOf(0.5f) }
    var height by remember { mutableStateOf(0.5f) }
    var room by remember { mutableStateOf(0.3f) }
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("PROCESAMIENTO ESPACIAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        PendingEngineNotice("Sin decorrelador de fase/surround implementado todavía — los controles guardan el valor pero no procesan audio.")
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = width, onValueChange = { width = it; audioEngine.surroundSetWidth(it) }, label = "WIDTH", accentColor = AccentEmerald, enabled = false)
            IVANNAKnob(value = height, onValueChange = { height = it; audioEngine.surroundSetHeight(it) }, label = "HEIGHT", accentColor = AccentEmerald, enabled = false)
            IVANNAKnob(value = room, onValueChange = { room = it; audioEngine.surroundSetRoom(it) }, label = "ROOM", accentColor = AccentEmerald, enabled = false)
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
