package com.ivannafusion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivannafusion.DSPState
import com.ivannafusion.IVANNAApplication
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun AIScreen(onBack: () -> Unit) {
    val bridge = IVANNAApplication.omegaBridge

    var aiEnabled     by remember { mutableStateOf<Boolean>(DSPState.aiEnabled) }
    var aiAutoAdapt   by remember { mutableStateOf<Boolean>(DSPState.aiAutoAdapt) }
    var aiSensitivity by remember { mutableFloatStateOf(DSPState.aiSensitivity) }

    // Telemetría AI leída del StateFlow del bridge
    val deviceTemp  by bridge.deviceTemp.collectAsState()
    val latencyMs   by bridge.latencyMs.collectAsState()
    val isConnected by bridge.isConnected.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        IVANNAHeader(title = "AI ADAPTATIVA", subtitle = "Control neuronal en tiempo real") {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan)
            }
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Estado de conexión ────────────────────────────────────────────
            IVANNACard {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("DAEMON", style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = MaterialTheme.shapes.small,
                            color = if (isConnected) AccentEmerald else SignalHot,
                            modifier = Modifier.size(8.dp)) {}
                        Text(if (isConnected) "CONECTADO" else "SIN SEÑAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) AccentEmerald else SignalHot)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.1f °C".format(deviceTemp),
                            style = MaterialTheme.typography.titleMedium, color = AccentCyan)
                        Text("TEMP", style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.1f ms".format(latencyMs),
                            style = MaterialTheme.typography.titleMedium, color = AccentCyan)
                        Text("LATENCIA", style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary)
                    }
                }
            }

            // ── AGC ───────────────────────────────────────────────────────────
            IVANNACard {
                Text("AGC — AUTO GAIN CONTROL",
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("ACTIVAR AGC",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("Mantiene -18 dBFS en el output",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    IVANNAToggle(
                        checked = aiEnabled,
                        onCheckedChange = { aiEnabled = it; bridge.setAiEnabled(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                IVANNASlider(
                    value       = aiSensitivity,
                    onValueChange = {
                        aiSensitivity = it
                        bridge.setAiSensitivity(it)
                    },
                    range       = 0f..1f,
                    label       = "VELOC.",
                    valueText   = if (aiSensitivity < 0.33f) "LENTO"
                                  else if (aiSensitivity < 0.66f) "MEDIO" else "RÁPIDO",
                    valueWidth  = 60.dp,
                    accentColor = AccentCyan,
                    enabled     = aiEnabled
                )
            }

            // ── Auto-adapt ────────────────────────────────────────────────────
            IVANNACard {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("AUTO-ADAPT TÉRMICO",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("Reduce intensity si temp > 42 °C · bypass si lat > 25 ms",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    IVANNAToggle(
                        checked = aiAutoAdapt,
                        onCheckedChange = { aiAutoAdapt = it; bridge.setAiAutoAdapt(it) }
                    )
                }
            }

            // ── Advertencia temp ──────────────────────────────────────────────
            if (deviceTemp >= 42f) {
                Surface(
                    color  = SignalHot.copy(alpha = 0.12f),
                    shape  = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠  Temperatura alta (${"%.1f".format(deviceTemp)} °C) — " +
                        if (aiAutoAdapt) "auto-adapt activo" else "activa auto-adapt",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = SignalHot,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
