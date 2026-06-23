package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivannafusion.dsp.DSPState
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // Valores REALES leídos del hardware (AudioManager.PROPERTY_OUTPUT_
    // SAMPLE_RATE/FRAMES_PER_BUFFER vía DSPState.detectRealHardwareCapabilities),
    // no texto fijo. La mayoría de dispositivos Android reportan 48000Hz
    // en su path de salida estándar — 192kHz/24-bit reales solo ocurren
    // con un DAC USB-C externo y su propio driver, no con el codec
    // interno del teléfono. Se muestra lo que el dispositivo realmente
    // soporta, sin forzar un número aspiracional.
    val sampleRateHz by DSPState.deviceSampleRateHz.collectAsState()
    val framesPerBuffer by DSPState.deviceFramesPerBuffer.collectAsState()
    val supportsHighRes by DSPState.deviceSupportsHighRes.collectAsState()
    val bufferLatencyMicros = if (sampleRateHz > 0 && framesPerBuffer > 0)
        (framesPerBuffer.toLong() * 1_000_000L / sampleRateHz) else 0L

    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary).verticalScroll(rememberScrollState())) {
        IVANNAHeader(title = "AJUSTES", subtitle = "Configuración del sistema") {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan) }
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("AUDIO (HARDWARE REAL DE ESTE DISPOSITIVO)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            SettingRow(label = "Sample Rate (nativo)", value = "$sampleRateHz Hz")
            SettingRow(label = "Audio interno", value = "32-bit float (AAudio/PCM_FLOAT)")
            SettingRow(label = "Frames por buffer", value = if (framesPerBuffer > 0) "$framesPerBuffer" else "No reportado por el sistema")
            SettingRow(label = "Latencia estimada", value = if (bufferLatencyMicros > 0) "${bufferLatencyMicros} μs" else "—")
            Spacer(Modifier.height(8.dp))
            if (!supportsHighRes) {
                Text(
                    "⚠ Este dispositivo reporta $sampleRateHz Hz como salida nativa — " +
                    "192kHz/24-bit \"reales\" requieren un DAC USB-C externo con su propio " +
                    "driver; el codec interno de la mayoría de teléfonos Android no opera ahí.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber
                )
            } else {
                Text(
                    "✓ Este dispositivo reporta $sampleRateHz Hz de forma nativa — calidad hi-res real.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentEmerald
                )
            }
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("INFORMACIÓN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            SettingRow(label = "Versión", value = "2.1.0")
            SettingRow(label = "Build", value = "2025.06.21")
            SettingRow(label = "Autor", value = "Luis Uriel Pimentel")
        }
        
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = AccentCyan)
    }
}
