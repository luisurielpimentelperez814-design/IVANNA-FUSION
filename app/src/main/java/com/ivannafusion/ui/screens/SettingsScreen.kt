package com.ivannafusion.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivannafusion.DSPState
import com.ivannafusion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = true

    // Valores REALES del hardware (ahora existen en DSPState)
    val sampleRate = DSPState.deviceSampleRateHz
    val framesPerBuffer = DSPState.deviceFramesPerBuffer
    val supportsHighRes = DSPState.deviceSupportsHighRes
    val bufferLatencyUs = DSPState.deviceBufferLatencyUs

    val sessionInfo = listOf(
        SessionInfo("Versión", "2.1.0", Icons.Outlined.Info),
        SessionInfo("Build", "2025.06.21", Icons.Outlined.Build),
        SessionInfo("Autor", "Luis Uriel Pimentel", Icons.Outlined.Person)
    )

    val hardwareItems = listOf(
        HardwareItem("Sample Rate", "$sampleRate Hz", Icons.Outlined.Speed, "Frecuencia de muestreo nativa"),
        HardwareItem("Formato interno", "32-bit float", Icons.Outlined.Analytics, "Procesamiento en coma flotante"),
        HardwareItem("Frames por buffer", if (framesPerBuffer > 0) "$framesPerBuffer" else "—", Icons.Outlined.ViewAgenda, "Tamaño del bloque de procesamiento"),
        HardwareItem("Latencia estimada", if (bufferLatencyUs > 0) "${bufferLatencyUs} μs" else "—", Icons.Outlined.Timer, "Latencia de ida y vuelta")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0F), Color(0xFF14141A))
                )
            )
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            AnimatedContent(transitionState.currentState) { _ ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White.copy(0.8f))
                    }
                    Text(
                        text = "CONFIGURACIÓN",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.W300,
                            letterSpacing = 2.5.sp,
                            fontSize = 20.sp,
                            color = Color.White.copy(0.9f)
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        item {
            AnimatedCard(delay = 0, transitionState) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Icon(Icons.Outlined.Memory, null, tint = AccentCyan.copy(0.7f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("HARDWARE REAL", style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W600, letterSpacing = 1.8.sp, color = AccentCyan.copy(0.8f), fontSize = 11.sp
                        ))
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (supportsHighRes) AccentEmerald else AccentAmber)
                        )
                    }
                    hardwareItems.forEach { item ->
                        HardwareParameterRow(item.icon, item.label, item.value, item.description, Modifier.padding(vertical = 6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (!supportsHighRes) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = AccentAmber.copy(0.08f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("192 kHz / 24-bit reales requieren DAC USB-C externo", style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentAmber.copy(0.8f), fontSize = 10.sp, letterSpacing = 0.3.sp
                                ))
                            }
                        }
                    } else {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = AccentEmerald.copy(0.08f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = AccentEmerald, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Hardware compatible con alta resolución", style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentEmerald.copy(0.8f), fontSize = 10.sp, letterSpacing = 0.3.sp
                                ))
                            }
                        }
                    }
                }
            }
        }

        item {
            AnimatedCard(delay = 100, transitionState) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Icon(Icons.Outlined.Info, null, tint = AccentCyan.copy(0.7f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("ACERCA DE", style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.W600, letterSpacing = 1.8.sp, color = AccentCyan.copy(0.8f), fontSize = 11.sp
                        ))
                    }
                    sessionInfo.forEach { info ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(info.icon, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(info.label, style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(0.5f), fontSize = 13.sp
                                ))
                            }
                            Text(info.value, style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.W400, color = Color.White.copy(0.9f), fontSize = 14.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ===== COMPONENTES =====

@Composable
private fun AnimatedCard(delay: Int, transitionState: MutableTransitionState<Boolean>, content: @Composable ColumnScope.() -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (transitionState.currentState) 1f else 0f,
        animationSpec = tween(600, delayMillis = delay, easing = FastOutSlowInEasing)
    )
    val offset by animateDpAsState(
        targetValue = if (transitionState.currentState) 0.dp else 20.dp,
        animationSpec = tween(500, delayMillis = delay, easing = FastOutSlowInEasing)
    )
    Card(
        modifier = Modifier.fillMaxWidth().offset(y = offset).alpha(alpha)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), clip = false,
                ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.5f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.06f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(Color(0x22FFFFFF), Color(0x0AFFFFFF))))
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) { content() }
    }
}

@Composable
private fun HardwareParameterRow(icon: ImageVector, label: String, value: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(0.6f), fontSize = 13.sp
                ))
            }
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.W400, color = Color.White.copy(0.9f), fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ))
        }
        if (description.isNotEmpty()) {
            Text(description, style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(0.25f), fontSize = 9.sp, letterSpacing = 0.3.sp
            ), modifier = Modifier.padding(start = 30.dp, top = 2.dp))
        }
    }
}

private data class SessionInfo(val label: String, val value: String, val icon: ImageVector)
private data class HardwareItem(val label: String, val value: String, val icon: ImageVector, val description: String = "")
