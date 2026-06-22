package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun PresetsScreen(audioEngine: AudioEngine, presetManager: PresetManager, onBack: () -> Unit) {
    val presets = listOf(
        "Studio Reference" to "Curva plana profesional",
        "Bass Boost" to "Refuerzo de graves +6dB",
        "Vocal Clarity" to "Realce de medios-agudos",
        "Live Room" to "Simulación de sala en vivo",
        "Cinematic" to "Respuesta tipo cine",
        "Electronic" to "Optimizado para EDM",
        "Acoustic" to "Instrumentos acústicos",
        "Podcast" to "Voz hablada optimizada"
    )
    var selectedPreset by remember { mutableStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary).verticalScroll(rememberScrollState())) {
        IVANNAHeader(title = "PRESETS", subtitle = "${presets.size} configuraciones") {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan) }
        }
        
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEachIndexed { index, preset ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selectedPreset == index) AccentCyan.copy(alpha = 0.1f) else BackgroundTertiary).clickable {
                        selectedPreset = index
                        // Antes esto solo cambiaba el resaltado visual: no se
                        // aplicaba ningún cambio real al motor DSP ni se
                        // persistía la selección. Ahora sí se conecta.
                        presetManager.loadPreset(preset.first)
                        audioEngine.setPreset(preset.first)
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = if (selectedPreset == index) AccentCyan else TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.first, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(preset.second, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    if (selectedPreset == index) StatusChip(text = "ACTIVO", color = AccentCyan)
                }
            }
        }
        
        Spacer(Modifier.height(100.dp))
    }
}
