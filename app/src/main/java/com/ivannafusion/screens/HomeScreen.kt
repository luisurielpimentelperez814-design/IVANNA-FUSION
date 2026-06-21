package com.ivannafusion.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        initialValue = 0f, targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    val spectrumData = remember(amplitudes) {
        List(64) { i -> ((sin(amplitudes + i * 0.3f) + 1f) / 2f * 0.85f + 0.1f).coerceIn(0f, 1f) }
    }

    LaunchedEffect(Unit) { engine.initialize(ctx); state = "Sistema Activo"; running = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = "IVANNA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Platinum)
                        Text(text = " FUSION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light, color = PrecisionCyan)
                    }
                },
                actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings", tint = Silver) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Obsidian)
            )
        },
        containerColor = Obsidian
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            
            MasterCard(title = "ESTADO DEL SISTEMA") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = state, style = MaterialTheme.typography.titleLarge, color = if (running) SignalGreen else WarningAmber, fontWeight = FontWeight.Bold)
                        Text(text = "Motor de audio cuántico activo", style = MaterialTheme.typography.bodyMedium, color = Silver)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = if (running) SignalGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)) {
                        Text(text = if (running) "ONLINE" else "STANDBY", style = MaterialTheme.typography.labelLarge,
                            color = if (running) SignalGreen else WarningAmber, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }

            MasterCard(title = "ANALIZADOR ESPECTRAL") {
                MasterSpectrumVisualizer(amplitudes = spectrumData, gradientColors = MasterGradient)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("20Hz", style = MaterialTheme.typography.labelMedium, color = Chrome)
                    Text("1kHz", style = MaterialTheme.typography.labelMedium, color = Chrome)
                    Text("20kHz", style = MaterialTheme.typography.labelMedium, color = Chrome)
                }
            }

            MasterCard(title = "NIVEL DE FUSIÓN CUÁNTICA") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MasterKnob(value = fusionLevel, onValueChange = { fusionLevel = it }, size = 120.dp, accentColor = PrecisionCyan)
                    Text(text = "${(fusionLevel * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = PrecisionCyan,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                }
            }

            MasterCard(title = "MÓDULOS AVANZADOS") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavButton("PF ENGINE", "Motor evolutivo", Icons.Default.Psychology, QuantumPurple) { nav.navigate("pf_engine") }
                    NavButton("ECUALIZADOR", "EQ dinámico", Icons.Default.Equalizer, SignalGreen) { nav.navigate("eq") }
                    NavButton("AUDIO ESPACIAL", "Sonido 3D", Icons.Default._3dRotation, PrecisionCyan) { nav.navigate("spatial") }
                    NavButton("IA ASISTENTE", "Análisis inteligente", Icons.Default.AutoAwesome, WarningAmber) { nav.navigate("ai") }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun NavButton(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Steel) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, title, tint = color, modifier = Modifier.size(28.dp)) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Platinum, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Silver)
            }
            Icon(Icons.Default.ChevronRight, "Navigate", tint = Chrome)
        }
    }
}
