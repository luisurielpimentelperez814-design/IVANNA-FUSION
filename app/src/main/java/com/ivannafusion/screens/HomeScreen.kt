package com.ivannafusion.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(engine: AudioEngine, presetMgr: PresetManager, nav: NavController) {
    var state by remember { mutableStateOf("Inicializando...") }
    var running by remember { mutableStateOf(false) }
    var fusionLevel by remember { mutableFloatStateOf(0.5f) }
    val ctx = LocalContext.current
    
    val infiniteTransition = rememberInfiniteTransition()
    val amplitudes by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    
    val spectrumData = remember(amplitudes) {
        List(32) { i -> ((sin(amplitudes + i * 0.4f) + 1f) / 2f * 0.8f + 0.1f).coerceIn(0f, 1f) }
    }

    LaunchedEffect(Unit) {
        engine.initialize(ctx)
        state = "Sistema Activo"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "IVANNA",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Text(
                            text = " FUSION",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Light,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    LEDIndicator(active = running, color = NeonGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (running) "EN VIVO" else "STANDBY",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (running) NeonGreen else Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visualizador de espectro principal
            GlassCard(borderColor = NeonCyan.copy(alpha = 0.5f)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ANALIZADOR DE ESPECTRO",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${AudioEngine.audio_fs_hz / 1000}kHz / ${AudioEngine.audio_bit_depth}bit",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SpectrumVisualizer(
                        amplitudes = if (running) spectrumData else List(32) { 0.1f },
                        barColor = NeonCyan
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("LATENCIA", "${engine.getLatencyMicros()}μs", NeonGreen)
                        StatItem("FASE", String.format("%.3f", engine.getPhaseErrorRms()), NeonPurple)
                        StatItem("FITNESS", String.format("%.2f", engine.getBestFitness()), NeonOrange)
                    }
                }
            }

            // Controles principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonButton(
                    onClick = { engine.startAudioCapture(); running = true; state = "Ejecutando" },
                    modifier = Modifier.weight(1f),
                    text = "▶ INICIAR",
                    color = NeonGreen,
                    enabled = !running
                )
                NeonButton(
                    onClick = { engine.stopAudioCapture(); running = false; state = "Detenido" },
                    modifier = Modifier.weight(1f),
                    text = "■ DETENER",
                    color = NeonRed,
                    enabled = running
                )
            }

            // Control de fusión
            GlassCard(borderColor = NeonPurple.copy(alpha = 0.5f)) {
                Column {
                    Text(
                        text = "NIVEL DE FUSIÓN",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonPurple,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfessionalKnob(
                            value = fusionLevel,
                            onValueChange = { fusionLevel = it; engine.setFusionLevel(it) },
                            label = "FUSIÓN",
                            unit = "",
                            accentColor = NeonPurple,
                            size = 100.dp
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SIMBIOSIS",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeonPurple,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "IA + DSP",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            LEDIndicator(active = fusionLevel > 0.3f, color = NeonPurple, size = 16.dp)
                        }
                    }
                }
            }

            // Navegación a módulos
            Text(
                text = "MÓDULOS",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            val modules = listOf(
                Triple("simbiosis", "Simbiosis", Icons.Default.Biotech),
                Triple("pf_engine", "PF Engine", Icons.Default.Psychology),
                Triple("eq", "EQ Dinámico", Icons.Default.GraphicEq),
                Triple("spatial", "Audio Espacial", Icons.Default.SurroundSound),
                Triple("ai", "Asistente IA", Icons.Default.SmartToy),
                Triple("presets", "Presets", Icons.Default.LibraryMusic),
                Triple("monitor", "Monitor", Icons.Default.MonitorHeart),
                Triple("settings", "Configuración", Icons.Default.Settings)
            )
            
            modules.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (route, label, icon) ->
                        ModuleCard(
                            label = label,
                            icon = icon,
                            onClick = { nav.navigate(route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModuleCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(100.dp)
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MediumSurface.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NeonCyan,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
