package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(engine: AudioEngine, presetMgr: PresetManager, nav: NavController) {
    var state by remember { mutableStateOf("Listo") }
    var running by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        engine.initialize(ctx)
        state = "Inicializado"
    }

    Scaffold(topBar = { TopAppBar(title = { Text("IVANNA FUSION") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Estado: $state", fontSize = 18.sp)
                    Text("Motor: ${if (engine.initialized) "Activo" else "Inactivo"}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { engine.startAudioCapture(); running = true; state = "Ejecutando" },
                    modifier = Modifier.weight(1f),
                    enabled = !running
                ) { Text("Iniciar") }
                Button(
                    onClick = { engine.stopAudioCapture(); running = false; state = "Detenido" },
                    modifier = Modifier.weight(1f),
                    enabled = running
                ) { Text("Detener") }
            }
            Spacer(Modifier.height(24.dp))
            listOf(
                "pf_engine" to "PF Engine",
                "presets" to "Presets",
                "spatial" to "Espacial",
                "eq" to "EQ Dinamico",
                "ai" to "Asistente IA",
                "simbiosis" to "Simbiosis",
                "monitor" to "Monitor",
                "settings" to "Configuracion"
            ).forEach { (route, label) ->
                Button(
                    onClick = { nav.navigate(route) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text(label) }
            }
        }
    }
}
