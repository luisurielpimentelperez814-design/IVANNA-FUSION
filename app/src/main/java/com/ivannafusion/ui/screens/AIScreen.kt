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
import com.ivannafusion.AudioEngine
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun AIScreen(audioEngine: AudioEngine, onBack: () -> Unit) {
    var aiEnabled by remember { mutableStateOf(true) }
    var autoAdapt by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableStateOf(0.7f) }
    
    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary).verticalScroll(rememberScrollState())) {
        IVANNAHeader(title = "MOTOR IA", subtitle = "Análisis adaptativo en tiempo real") {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan) }
        }

        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "⚠ Sin modelo de clasificación cargado todavía — los valores de abajo son " +
                "placeholders fijos, no una lectura en vivo del audio.",
                style = MaterialTheme.typography.labelSmall,
                color = AccentAmber
            )
        }

        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("MOTOR IA", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Detección: ${audioEngine.aiGetDetectedGenre()} (sin clasificador activo)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("Confianza: ${String.format("%.0f%%", audioEngine.aiGetConfidence() * 100)} (valor fijo)", style = MaterialTheme.typography.labelMedium, color = AccentCyan.copy(alpha = 0.6f))
                    Text("Tempo: ${String.format("%.1f BPM", audioEngine.aiGetTempo())} (valor fijo)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                IVANNAToggle(checked = aiEnabled, onCheckedChange = { aiEnabled = it; audioEngine.aiSetEnabled(it) })
            }
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("AUTO-ADAPTACIÓN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            IVANNAToggle(checked = autoAdapt, onCheckedChange = { autoAdapt = it; audioEngine.aiSetAutoAdapt(it) })
            Spacer(Modifier.height(16.dp))
            IVANNAKnob(value = sensitivity, onValueChange = { sensitivity = it; audioEngine.aiSetSensitivity(it) }, label = "SENSIBILIDAD", range = 0f..1f, accentColor = AccentViolet)
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("CURVA ACTUAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(audioEngine.aiGetCurrentCurveName(), style = MaterialTheme.typography.titleLarge, color = AccentCyan)
            Text(audioEngine.aiGetCurrentCurveDescription(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            IVANNAButton(text = "APLICAR CURVA", onClick = { audioEngine.aiApplyCurrentCurve() }, accent = AccentCyan)
        }
        
        Spacer(Modifier.height(100.dp))
    }
}
