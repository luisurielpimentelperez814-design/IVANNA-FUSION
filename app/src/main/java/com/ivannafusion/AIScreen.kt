package com.ivannafusion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AIScreen(audioEngine: AudioEngine) {
    var generation by remember { mutableIntStateOf(0) }
    var fitness by remember { mutableFloatStateOf(0f) }
    var mutationRate by remember { mutableFloatStateOf(0.01f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🤖 Asistente IA", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Generación: $generation")
                Text("Mejor Fitness: $fitness")
                Text("Tasa de Mutación: $mutationRate")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            audioEngine.initializeEvolution()
            generation = audioEngine.getGeneration()
            fitness = audioEngine.getBestFitness()
        }) {
            Text("Inicializar Evolución")
        }

        Button(onClick = {
            audioEngine.evolveStep()
            generation = audioEngine.getGeneration()
            fitness = audioEngine.getBestFitness()
        }) {
            Text("Evolucionar")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Tasa de Mutación: $mutationRate")
        Slider(
            value = mutationRate,
            onValueChange = {
                mutationRate = it
                audioEngine.setMutationRate(it)
            },
            valueRange = 0f..1f
        )
    }
}
