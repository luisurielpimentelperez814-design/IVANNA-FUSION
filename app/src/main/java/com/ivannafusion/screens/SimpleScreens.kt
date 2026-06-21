package com.ivannafusion.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, nav: NavController) {
    TopAppBar(
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Platinum) },
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrecisionCyan) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Obsidian)
    )
}

@Composable
fun PFEngineScreen(engine: AudioEngine, nav: NavController) {
    var gen by remember { mutableIntStateOf(0) }
    var fit by remember { mutableFloatStateOf(0f) }
    var mutationRate by remember { mutableFloatStateOf(engine.getMutationRate()) }

    Scaffold(topBar = { BackBar("PF ENGINE", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MasterCard(title = "MOTOR EVOLUTIVO") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Generación", style = MaterialTheme.typography.labelLarge, color = Silver)
                            Text("$gen", style = MaterialTheme.typography.headlineMedium, color = QuantumPurple, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Fitness", style = MaterialTheme.typography.labelLarge, color = Silver)
                            Text("${(fit * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = SignalGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(onClick = { gen++; fit = (fit + 0.05f).coerceAtMost(1f); engine.evolve() },
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = QuantumPurple),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.PlayArrow, "Evolve")
                        Spacer(Modifier.width(8.dp))
                        Text("EVOLUCIONAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
            MasterCard(title = "TASA DE MUTACIÓN") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MasterKnob(value = mutationRate, onValueChange = { mutationRate = it; engine.setMutationRate(it) },
                        size = 100.dp, accentColor = QuantumPurple)
                    Text("${(mutationRate * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = QuantumPurple,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
fun PresetsScreen(presetMgr: PresetManager, engine: AudioEngine, nav: NavController) {
    val presets = listOf("Clean Studio", "Marshall Crunch", "Vox Sparkle", "70s Rock", "Psychedelic")
    var selected by remember { mutableStateOf("Clean Studio") }

    Scaffold(topBar = { BackBar("PRESETS", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            presets.forEach { preset ->
                Surface(onClick = { selected = preset; presetMgr.loadPreset(preset.lowercase().replace(" ", "_")) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = if (selected == preset) PrecisionCyan.copy(alpha = 0.15f) else Steel,
                    border = BorderStroke(2.dp, if (selected == preset) PrecisionCyan else Color.Transparent)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.MusicNote, preset, tint = if (selected == preset) PrecisionCyan else Silver, modifier = Modifier.size(32.dp))
                        Text(preset, style = MaterialTheme.typography.titleMedium, color = if (selected == preset) PrecisionCyan else Platinum,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (selected == preset) Icon(Icons.Default.CheckCircle, "Selected", tint = PrecisionCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(engine: AudioEngine, nav: NavController) {
    var bufferSize by remember { mutableIntStateOf(192) }
    var sampleRate by remember { mutableIntStateOf(48000) }

    Scaffold(topBar = { BackBar("CONFIGURACIÓN", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MasterCard(title = "BUFFER DE AUDIO") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(96, 192, 256, 512).forEach { size ->
                        Surface(onClick = { bufferSize = size }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            color = if (bufferSize == size) PrecisionCyan.copy(alpha = 0.15f) else Steel) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("$size samples", style = MaterialTheme.typography.bodyLarge, color = Platinum)
                                if (bufferSize == size) Icon(Icons.Default.CheckCircle, "Selected", tint = PrecisionCyan)
                            }
                        }
                    }
                }
            }
            MasterCard(title = "FRECUENCIA DE MUESTREO") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(44100, 48000, 96000).forEach { rate ->
                        Surface(onClick = { sampleRate = rate }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            color = if (sampleRate == rate) PrecisionCyan.copy(alpha = 0.15f) else Steel) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${rate/1000}kHz", style = MaterialTheme.typography.bodyLarge, color = Platinum)
                                if (sampleRate == rate) Icon(Icons.Default.CheckCircle, "Selected", tint = PrecisionCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIScreen(engine: AudioEngine, nav: NavController) {
    var analysis by remember { mutableStateOf("Listo para analizar") }
    var confidence by remember { mutableFloatStateOf(0f) }

    Scaffold(topBar = { BackBar("IA ASISTENTE", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MasterCard(title = "ANÁLISIS NEURAL") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(analysis, style = MaterialTheme.typography.bodyLarge, color = Platinum)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Confianza", style = MaterialTheme.typography.labelLarge, color = Silver)
                        Text("${(confidence * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = WarningAmber, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { analysis = "Analizando contenido espectral..."; confidence = 0.87f },
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.AutoAwesome, "Analyze")
                        Spacer(Modifier.width(8.dp))
                        Text("ANALIZAR AHORA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SpatialScreen(engine: AudioEngine, nav: NavController) {
    var posX by remember { mutableFloatStateOf(0.5f) }
    var posY by remember { mutableFloatStateOf(0.5f) }

    Scaffold(topBar = { BackBar("AUDIO ESPACIAL", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MasterCard(title = "POSICIÓN 3D") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MasterTouchPad(x = posX, y = posY, onPositionChange = { x, y -> posX = x; posY = y }, size = 240.dp)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("X: ${(posX * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Silver)
                    Text("Y: ${(posY * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Silver)
                }
            }
        }
    }
}

@Composable
fun DynamicEQScreen(engine: AudioEngine, nav: NavController) {
    var bands by remember { mutableStateOf(listOf(0.5f, 0.6f, 0.4f, 0.7f, 0.5f)) }

    Scaffold(topBar = { BackBar("ECUALIZADOR DINÁMICO", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MasterCard(title = "BANDAS DE FRECUENCIA") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    bands.forEachIndexed { index, value ->
                        MasterKnob(value = value, onValueChange = { newValue -> bands = bands.toMutableList().also { it[index] = newValue } },
                            size = 70.dp, label = "${(index + 1) * 200}Hz", accentColor = SignalGreen)
                    }
                }
            }
        }
    }
}

@Composable
fun SimbiosisScreen(engine: AudioEngine, nav: NavController) {
    var sync by remember { mutableFloatStateOf(0.75f) }

    Scaffold(topBar = { BackBar("SIMBIOSIS", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MasterCard(title = "SINCRONIZACIÓN NEURAL") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MasterKnob(value = sync, onValueChange = { sync = it }, size = 120.dp, accentColor = PrecisionCyan)
                    Text("${(sync * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = PrecisionCyan,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(engine: AudioEngine, nav: NavController) {
    var cpu by remember { mutableFloatStateOf(0.35f) }
    var temp by remember { mutableFloatStateOf(42f) }

    Scaffold(topBar = { BackBar("MONITOR", nav) }, containerColor = Obsidian) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MasterCard(title = "RENDIMIENTO") {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("CPU", style = MaterialTheme.typography.labelLarge, color = Silver)
                            Text("${(cpu * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = SignalGreen, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TEMP", style = MaterialTheme.typography.labelLarge, color = Silver)
                            Text("${temp.toInt()}°C", style = MaterialTheme.typography.headlineMedium, color = if (temp > 60) CriticalRed else SignalGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
