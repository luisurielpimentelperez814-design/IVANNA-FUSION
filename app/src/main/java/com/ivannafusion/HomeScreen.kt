package com.ivannafusion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    audioEngine: AudioEngine,
    presetManager: PresetManager,
    onNavigateToPFEngine: () -> Unit,
    onNavigateToPresets: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var engineState by remember { mutableStateOf("Inicializando...") }
    var isAudioRunning by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        audioEngine.initialize(context)
        engineState = "Listo"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "🎵 IVANNA FUSION",
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Estado del Motor", fontSize = 14.sp)
                Text(engineState, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    audioEngine.startAudioCapture()
                    isAudioRunning = true
                    engineState = "En ejecución"
                },
                modifier = Modifier.weight(1f),
                enabled = !isAudioRunning
            ) {
                Text("▶ Iniciar")
            }

            Button(
                onClick = {
                    audioEngine.stopAudioCapture()
                    isAudioRunning = false
                    engineState = "Detenido"
                },
                modifier = Modifier.weight(1f),
                enabled = isAudioRunning
            ) {
                Text("⏹ Detener")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onNavigateToPFEngine, modifier = Modifier.fillMaxWidth()) {
            Text("PF Engine")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onNavigateToPresets, modifier = Modifier.fillMaxWidth()) {
            Text("Presets")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onNavigateToSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Configuración")
        }
    }
}
