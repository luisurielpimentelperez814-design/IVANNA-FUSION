package com.ivannafusion.screens

import androidx.compose.foundation.background
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
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
        },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonCyan)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack)
    )
}

@Composable
fun PFEngineScreen(engine: AudioEngine, nav: NavController) {
    var gen by remember { mutableIntStateOf(0) }
    var fit by remember { mutableFloatStateOf(0f) }
    var mutationRate by remember { mutableFloatStateOf(engine.getMutationRate()) }

    Scaffold(topBar = { BackBar("PF ENGINE", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonPurple.copy(alpha = 0.5f)) {
                Column {
                    Text("MOTOR EVOLUTIVO", style = MaterialTheme.typography.titleLarge, color = NeonPurple, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("GEN", gen.toString(), NeonCyan)
                        StatItem("FITNESS", String.format("%.4f", fit), NeonGreen)
                        StatItem("POBLACIÓN", "128", NeonOrange)
                    }
                }
            }

            GlassCard(borderColor = NeonCyan.copy(alpha = 0.5f)) {
                Column {
                    Text("PARÁMETROS", style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProfessionalKnob(
                            value = mutationRate,
                            onValueChange = { mutationRate = it },
                            label = "MUTACIÓN",
                            minValue = 0f,
                            maxValue = 0.1f,
                            accentColor = NeonOrange,
                            size = 90.dp
                        )
                        ProfessionalKnob(
                            value = fit,
                            onValueChange = {},
                            label = "FITNESS",
                            minValue = 0f,
                            maxValue = 1f,
                            accentColor = NeonGreen,
                            size = 90.dp
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(
                    onClick = { engine.initializeEvolution(); gen = engine.getGeneration(); fit = engine.getBestFitness() },
                    modifier = Modifier.weight(1f),
                    text = "INICIALIZAR",
                    color = NeonCyan
                )
                NeonButton(
                    onClick = { engine.evolveStep(); gen = engine.getGeneration(); fit = engine.getBestFitness() },
                    modifier = Modifier.weight(1f),
                    text = "EVOLUCIONAR",
                    color = NeonPurple
                )
            }
        }
    }
}

@Composable
fun PresetsScreen(presetMgr: PresetManager, engine: AudioEngine, nav: NavController) {
    val presets = presetMgr.getPresetList()

    Scaffold(topBar = { BackBar("PRESETS", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(borderColor = AccentGold.copy(alpha = 0.5f)) {
                Text("BIBLIOTECA DE PRESETS", style = MaterialTheme.typography.titleLarge, color = AccentGold, fontWeight = FontWeight.Bold)
            }
            
            presets.forEach { preset ->
                Card(
                    onClick = { presetMgr.loadPreset(preset, {}, {}); engine.setPreset(preset) },
                    modifier = Modifier.fillMaxWidth().border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MediumSurface.copy(alpha = 0.6f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.LibraryMusic, preset, tint = NeonCyan, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preset, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Preset profesional", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                        }
                        Icon(Icons.Default.PlayArrow, "Load", tint = NeonGreen)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(engine: AudioEngine, nav: NavController) {
    Scaffold(topBar = { BackBar("CONFIGURACIÓN", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonCyan.copy(alpha = 0.5f)) {
                Column {
                    Text("CONFIGURACIÓN DE AUDIO", style = MaterialTheme.typography.titleLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("SAMPLE RATE", "${AudioEngine.audio_fs_hz} Hz", NeonGreen)
                        StatItem("BIT DEPTH", "${AudioEngine.audio_bit_depth}", NeonPurple)
                        StatItem("LATENCIA", "${AudioEngine.audio_latencia_us}μs", NeonOrange)
                    }
                }
            }

            Text("CALIDAD DE AUDIO", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(
                    onClick = { engine.setPreferredAudioConfig(48000, 16) },
                    modifier = Modifier.weight(1f),
                    text = "48kHz/16bit",
                    color = NeonCyan
                )
                NeonButton(
                    onClick = { engine.setPreferredAudioConfig(96000, 24) },
                    modifier = Modifier.weight(1f),
                    text = "96kHz/24bit",
                    color = NeonPurple
                )
            }
        }
    }
}

@Composable
fun AIScreen(engine: AudioEngine, nav: NavController) {
    Scaffold(topBar = { BackBar("ASISTENTE IA", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonGreen.copy(alpha = 0.5f)) {
                Column {
                    Text("ANÁLISIS INTELIGENTE", style = MaterialTheme.typography.titleLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("GÉNERO", engine.aiGetDetectedGenre(), NeonCyan)
                        StatItem("CONFIANZA", String.format("%.1f%%", engine.aiGetConfidence() * 100), NeonGreen)
                        StatItem("TEMPO", "${String.format("%.0f", engine.aiGetTempo())} BPM", NeonOrange)
                    }
                }
            }

            GlassCard(borderColor = NeonPurple.copy(alpha = 0.5f)) {
                Column {
                    Text("CURVA ACTUAL", style = MaterialTheme.typography.labelLarge, color = NeonPurple, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(engine.aiGetCurrentCurveName(), style = MaterialTheme.typography.headlineMedium, color = NeonPurple, fontWeight = FontWeight.Bold)
                    Text(engine.aiGetCurrentCurveDesc(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
                }
            }

            NeonButton(
                onClick = { engine.aiApplyCurrentCurve() },
                modifier = Modifier.fillMaxWidth(),
                text = "APLICAR CURVA IA",
                color = NeonGreen
            )
        }
    }
}

@Composable
fun SpatialScreen(engine: AudioEngine, nav: NavController) {
    var width by remember { mutableFloatStateOf(0.5f) }

    Scaffold(topBar = { BackBar("AUDIO ESPACIAL", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonBlue.copy(alpha = 0.5f)) {
                Column {
                    Text("CAMPO ESTÉREO", style = MaterialTheme.typography.titleLarge, color = NeonBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ProfessionalKnob(
                            value = width,
                            onValueChange = { width = it; engine.surroundSetWidth(it); engine.widenerSetWidth(it * 2f) },
                            label = "WIDTH",
                            minValue = 0f,
                            maxValue = 1f,
                            accentColor = NeonBlue,
                            size = 120.dp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonButton(
                            onClick = { width = 0f; engine.surroundSetWidth(0f); engine.widenerSetWidth(0f) },
                            modifier = Modifier.weight(1f),
                            text = "MONO",
                            color = NeonRed
                        )
                        NeonButton(
                            onClick = { width = 0.5f; engine.surroundSetWidth(0.5f); engine.widenerSetWidth(1f) },
                            modifier = Modifier.weight(1f),
                            text = "ESTÉREO",
                            color = NeonCyan
                        )
                        NeonButton(
                            onClick = { width = 1f; engine.surroundSetWidth(1f); engine.widenerSetWidth(2f) },
                            modifier = Modifier.weight(1f),
                            text = "WIDE",
                            color = NeonPurple
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicEQScreen(engine: AudioEngine, nav: NavController) {
    val gains = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }

    Scaffold(topBar = { BackBar("EQ DINÁMICO", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonOrange.copy(alpha = 0.5f)) {
                Column {
                    Text("ECUALIZADOR 5 BANDAS", style = MaterialTheme.typography.titleLarge, color = NeonOrange, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val bandNames = listOf("60Hz", "250Hz", "1kHz", "4kHz", "12kHz")
                        val bandColors = listOf(NeonRed, NeonOrange, NeonGreen, NeonCyan, NeonPurple)
                        
                        bandNames.forEachIndexed { index, name ->
                            ProfessionalKnob(
                                value = gains[index],
                                onValueChange = { gains[index] = it; engine.eqSetGain(index, it) },
                                label = name,
                                unit = "dB",
                                minValue = -12f,
                                maxValue = 12f,
                                accentColor = bandColors[index],
                                size = 70.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimbiosisScreen(engine: AudioEngine, nav: NavController) {
    var fusion by remember { mutableFloatStateOf(0.5f) }

    Scaffold(topBar = { BackBar("SIMBIOSIS", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonPurple.copy(alpha = 0.5f)) {
                Column {
                    Text("FUSIÓN IA + DSP", style = MaterialTheme.typography.titleLarge, color = NeonPurple, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ProfessionalKnob(
                            value = fusion,
                            onValueChange = { fusion = it; engine.setFusionLevel(it) },
                            label = "FUSIÓN",
                            minValue = 0f,
                            maxValue = 1f,
                            accentColor = NeonPurple,
                            size = 120.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LEDIndicator(active = engine.initialized, color = NeonGreen, size = 16.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(if (engine.initialized) "ACTIVO" else "INACTIVO", style = MaterialTheme.typography.labelLarge, color = if (engine.initialized) NeonGreen else Color.White.copy(alpha = 0.5f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("LATENCIA", "${engine.getLatencyMicros()}μs", NeonCyan)
                        StatItem("ERROR FASE", String.format("%.4f", engine.getPhaseErrorRms()), NeonOrange)
                    }
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(engine: AudioEngine, nav: NavController) {
    var lat by remember { mutableLongStateOf(0L) }

    Scaffold(topBar = { BackBar("MONITOR", nav) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(borderColor = NeonGreen.copy(alpha = 0.5f)) {
                Column {
                    Text("MÉTRICAS EN TIEMPO REAL", style = MaterialTheme.typography.titleLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        VUMeter(level = engine.getMomentaryLoudness(), label = "LUFS")
                        VUMeter(level = engine.getPeakLevel(), label = "PEAK")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("LATENCIA", "$lat μs", NeonCyan)
                        StatItem("MOMENTARY", String.format("%.1f LUFS", engine.getMomentaryLoudness()), NeonGreen)
                        StatItem("CORR", String.format("%.2f", engine.getCorrelation()), NeonPurple)
                    }
                }
            }

            NeonButton(
                onClick = { lat = engine.getLatencyMicros() },
                modifier = Modifier.fillMaxWidth(),
                text = "ACTUALIZAR MÉTRICAS",
                color = NeonCyan
            )
        }
    }
}
