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
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("ECUALIZADOR PARAMÉTRICO 10 BANDAS", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            bands.forEachIndexed { index, label ->
                IVANNAKnob(
                    value = gains[index],
                    onValueChange = { newValue ->
                        gains = gains.toMutableList().also { it[index] = newValue }
                        audioEngine.eqSetGain(index, newValue * 24f - 12f)
                    },
                    size = 64.dp,
                    range = 0f..1f,
                    label = label,
                    unit = "",
                    accentColor = AccentCyan
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
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("COMPRESOR DINÁMICO", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = threshold, onValueChange = { threshold = it; audioEngine.compSetThreshold(it * -60f) }, size = 72.dp, label = "THRESH", unit = "dB", accentColor = AccentMagenta)
            IVANNAKnob(value = ratio, onValueChange = { ratio = it; audioEngine.compSetRatio(1f + it * 19f) }, size = 72.dp, label = "RATIO", unit = ":1", accentColor = AccentMagenta)
            IVANNAKnob(value = attack, onValueChange = { attack = it; audioEngine.compSetAttack(it * 100f) }, size = 72.dp, label = "ATK", unit = "ms", accentColor = AccentMagenta)
            IVANNAKnob(value = release, onValueChange = { release = it; audioEngine.compSetRelease(it * 1000f) }, size = 72.dp, label = "REL", unit = "ms", accentColor = AccentMagenta)
        }
    }}

@Composable
private fun ConvolverPanel(audioEngine: AudioEngine) {
    var enabled by remember { mutableStateOf(false) }
    var mix by remember { mutableStateOf(0.5f) }
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CONVOLVER", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            IVANNAToggle(checked = enabled, onCheckedChange = { enabled = it; audioEngine.convolverSetEnabled(it) })
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = mix, onValueChange = { mix = it; audioEngine.convolverSetMix(it) }, label = "MIX", unit = "%", accentColor = AccentViolet)
        }
    }
}

@Composable
private fun SpatialPanel(audioEngine: AudioEngine) {
    var width by remember { mutableStateOf(0.5f) }
    var height by remember { mutableStateOf(0.5f) }
    var room by remember { mutableStateOf(0.3f) }
    
    IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("PROCESAMIENTO ESPACIAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IVANNAKnob(value = width, onValueChange = { width = it; audioEngine.surroundSetWidth(it) }, label = "WIDTH", accentColor = AccentEmerald)
            IVANNAKnob(value = height, onValueChange = { height = it; audioEngine.surroundSetHeight(it) }, label = "HEIGHT", accentColor = AccentEmerald)
            IVANNAKnob(value = room, onValueChange = { room = it; audioEngine.surroundSetRoom(it) }, label = "ROOM", accentColor = AccentEmerald)
        }
    }
}
