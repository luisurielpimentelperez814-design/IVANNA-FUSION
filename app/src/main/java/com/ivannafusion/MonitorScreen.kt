package com.ivannafusion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MonitorScreen(audioEngine: AudioEngine) {
    var latency by remember { mutableLongStateOf(0L) }
    var phaseError by remember { mutableFloatStateOf(0f) }
    var generation by remember { mutableIntStateOf(0) }
    var fitness by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📊 Monitor", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sample Rate: ${AudioEngine.audio_fs_hz} Hz")
                Text("Bit Depth: ${AudioEngine.audio_bit_depth} bits")
                Text("Latencia: $latency μs")
                Text("Phase Error RMS: $phaseError")
                Text("Generación: $generation")
                Text("Fitness: $fitness")
            }        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            latency = audioEngine.getLatencyMicros()
            phaseError = audioEngine.getPhaseErrorRms()
            generation = audioEngine.getGeneration()
            fitness = audioEngine.getBestFitness()
        }) {
            Text("Actualizar Métricas")
        }

        Button(onClick = {
            audioEngine.evolveStep()
            generation = audioEngine.getGeneration()
            fitness = audioEngine.getBestFitness()
        }) {
            Text("Evolucionar")
        }

        Button(onClick = {
            audioEngine.setFusionLevel(0.5f)
        }) {
            Text("Establecer Fusión")
        }
    }
}
