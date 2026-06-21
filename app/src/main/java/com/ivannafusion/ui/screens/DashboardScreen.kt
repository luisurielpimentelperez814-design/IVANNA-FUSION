package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ivannafusion.AudioEngine
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(audioEngine: AudioEngine, onNavigate: (String) -> Unit) {
    var isProcessing by remember { mutableStateOf(true) }
    var spectrum by remember { mutableStateOf(List(32) { 0.3f }) }
    var loudness by remember { mutableStateOf(0.6f) }
    var correlation by remember { mutableStateOf(0.85f) }
    
    LaunchedEffect(isProcessing) {
        if (!isProcessing) return@LaunchedEffect
        while (isProcessing) {
            spectrum = List(32) { (0.2f + kotlin.random.Random.nextFloat() * 0.7f) }
            loudness = (audioEngine.getMomentaryLoudness() + 30f) / 30f
            correlation = audioEngine.getCorrelation()
            delay(80)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        IVANNAHeader(title = "IVANNA FUSION", subtitle = "Motor Evolutivo Activo") {
            StatusChip(text = if (isProcessing) "LIVE" else "IDLE", color = if (isProcessing) SignalCool else SignalMute)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { isProcessing = !isProcessing }) {                Icon(imageVector = if (isProcessing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Toggle", tint = AccentCyan)
            }
        }
        
        IVANNACard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(180.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SpectrumVisualizer(magnitudes = spectrum, accentColor = AccentCyan, modifier = Modifier.fillMaxSize())
                
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("L", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        VUMeter(level = loudness, modifier = Modifier.height(100.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("R", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        VUMeter(level = loudness * 0.95f, modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(label = "LATENCIA", value = "${audioEngine.getLatencyMicros()}μs", color = AccentCyan, modifier = Modifier.weight(1f))
            StatCard(label = "GENERACIÓN", value = "${audioEngine.getGeneration()}", color = AccentViolet, modifier = Modifier.weight(1f))
            StatCard(label = "FITNESS", value = String.format("%.3f", audioEngine.getBestFitness()), color = AccentEmerald, modifier = Modifier.weight(1f))
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("CORRELACIÓN ESTÉREO", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            CorrelationMeter(correlation = correlation)
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("ACCESO RÁPIDO", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionRow(icon = Icons.Default.GraphicEq, title = "Ecualizador", subtitle = "10 bandas paramétricas", onClick = { onNavigate("effects") })
                QuickActionRow(icon = Icons.Default.AutoAwesome, title = "IA Adaptativa", subtitle = "Detección de género activa", onClick = { onNavigate("ai") })
                QuickActionRow(icon = Icons.Default.LibraryMusic, title = "Presets", subtitle = "12 configuraciones", onClick = { onNavigate("presets") })            }
        }
        
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(BackgroundTertiary).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun QuickActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1F2937)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}
