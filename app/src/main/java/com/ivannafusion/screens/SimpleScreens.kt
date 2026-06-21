package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, nav: NavController) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        }
    )
}

@Composable
fun PFEngineScreen(engine: AudioEngine, nav: NavController) {
    var gen by remember { mutableIntStateOf(0) }
    var fit by remember { mutableFloatStateOf(0f) }
    Scaffold(topBar = { BackBar("PF Engine", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Generacion: $gen", fontSize = 18.sp)
                    Text("Fitness: $fit", fontSize = 18.sp)
                    Text("Mutacion: ${engine.getMutationRate()}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { engine.initializeEvolution(); gen = engine.getGeneration(); fit = engine.getBestFitness() }, Modifier.fillMaxWidth()) {
                Text("Inicializar")
            }
            Button(onClick = { engine.evolveStep(); gen = engine.getGeneration(); fit = engine.getBestFitness() }, Modifier.fillMaxWidth()) {
                Text("Evolucionar")
            }
        }
    }}

@Composable
fun PresetsScreen(presetMgr: PresetManager, engine: AudioEngine, nav: NavController) {
    val presets = presetMgr.getPresetList()
    Scaffold(topBar = { BackBar("Presets", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            presets.forEach { preset ->
                Button(onClick = { presetMgr.loadPreset(preset, {}, {}); engine.setPreset(preset) }, Modifier.fillMaxWidth().padding(4.dp)) {
                    Text(preset)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(engine: AudioEngine, nav: NavController) {
    Scaffold(topBar = { BackBar("Configuracion", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Text("Audio Config", fontSize = 20.sp)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sample Rate: ${AudioEngine.audio_fs_hz} Hz")
                    Text("Bit Depth: ${AudioEngine.audio_bit_depth}")
                    Text("Latencia: ${AudioEngine.audio_latencia_us} us")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { engine.setPreferredAudioConfig(48000, 16) }, Modifier.fillMaxWidth()) { Text("48kHz / 16bit") }
            Button(onClick = { engine.setPreferredAudioConfig(96000, 24) }, Modifier.fillMaxWidth()) { Text("96kHz / 24bit") }
        }
    }
}

@Composable
fun AIScreen(engine: AudioEngine, nav: NavController) {
    Scaffold(topBar = { BackBar("Asistente IA", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Genero: ${engine.aiGetDetectedGenre()}")
                    Text("Confianza: ${engine.aiGetConfidence()}")
                    Text("Tempo: ${engine.aiGetTempo()} BPM")
                    Text("Curva: ${engine.aiGetCurrentCurveName()}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { engine.aiApplyCurrentCurve() }, Modifier.fillMaxWidth()) { Text("Aplicar Curva") }
        }    }
}

@Composable
fun SpatialScreen(engine: AudioEngine, nav: NavController) {
    var width by remember { mutableFloatStateOf(0.5f) }
    Scaffold(topBar = { BackBar("Audio Espacial", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Text("Width: $width")
            Slider(value = width, onValueChange = { width = it; engine.surroundSetWidth(it) })
            Spacer(Modifier.height(16.dp))
            Button(onClick = { engine.widenerSetWidth(1.0f) }, Modifier.fillMaxWidth()) { Text("Maximo") }
        }
    }
}

@Composable
fun DynamicEQScreen(engine: AudioEngine, nav: NavController) {
    Scaffold(topBar = { BackBar("EQ Dinamico", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            for (i in 0..4) {
                Text("Band $i")
                var gain by remember { mutableFloatStateOf(0f) }
                Slider(value = gain, onValueChange = { gain = it; engine.eqSetGain(i, it) }, valueRange = -12f..12f)
            }
        }
    }
}

@Composable
fun SimbiosisScreen(engine: AudioEngine, nav: NavController) {
    var fusion by remember { mutableFloatStateOf(0.5f) }
    Scaffold(topBar = { BackBar("Simbiosis", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Text("Nivel de Fusion: $fusion", fontSize = 20.sp)
            Slider(value = fusion, onValueChange = { fusion = it; engine.setFusionLevel(it) })
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Estado: ${if (engine.initialized) "Activo" else "Inactivo"}")
                    Text("Latencia: ${engine.getLatencyMicros()} us")
                    Text("Phase Error: ${engine.getPhaseErrorRms()}")
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(engine: AudioEngine, nav: NavController) {    var lat by remember { mutableLongStateOf(0L) }
    Scaffold(topBar = { BackBar("Monitor", nav) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Button(onClick = { lat = engine.getLatencyMicros() }, Modifier.fillMaxWidth()) { Text("Actualizar") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Latencia: $lat us")
                    Text("Momentary: ${engine.getMomentaryLoudness()} LUFS")
                    Text("Peak: ${engine.getPeakLevel()} dB")
                    Text("Correlation: ${engine.getCorrelation()}")
                }
            }
        }
    }
}
