package com.ivannafusion.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivannafusion.DSPState
import com.ivannafusion.ui.theme.*
import kotlin.math.abs

/**
 * Pantalla de configuración con diseño de estudio de audio profesional.
 * Inspirada en interfaces de hardware y software de gama alta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // Estado animado para entrada de las tarjetas (efecto cascada)
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = true

    // Valores reales del hardware (se asume que DSPState los expone correctamente)
    val sampleRate = DSPState.deviceSampleRateHz
    val framesPerBuffer = DSPState.deviceFramesPerBuffer
    val supportsHighRes = DSPState.deviceSupportsHighRes
    val bufferLatencyUs = if (sampleRate > 0 && framesPerBuffer > 0)
        (framesPerBuffer.toLong() * 1_000_000L / sampleRate) else 0L

    // Información de la sesión (simulada, puede venir de un ViewModel)
    val sessionInfo = listOf(
        SessionInfo("Versión", "2.1.0", Icons.Outlined.Info),
        SessionInfo("Build", "2025.06.21", Icons.Outlined.Build),
        SessionInfo("Autor", "Luis Uriel Pimentel", Icons.Outlined.Person)
    )

    // Tarjetas de hardware (información técnica)
    val hardwareItems = listOf(
        HardwareItem(
            label = "Sample Rate",
            value = "$sampleRate Hz",
            icon = Icons.Outlined.Speed,
            description = "Frecuencia de muestreo nativa del dispositivo"
        ),
        HardwareItem(
            label = "Formato interno",
            value = "32-bit float",
            icon = Icons.Outlined.Analytics,
            description = "Procesamiento en coma flotante (AAudio/PCM_FLOAT)"
        ),
        HardwareItem(
            label = "Frames por buffer",
            value = if (framesPerBuffer > 0) "$framesPerBuffer" else "—",
            icon = Icons.Outlined.ViewAgenda,
            description = "Tamaño del bloque de procesamiento"
        ),
        HardwareItem(
            label = "Latencia estimada",
            value = if (bufferLatencyUs > 0) "${bufferLatencyUs} μs" else "—",
            icon = Icons.Outlined.Timer,
            description = "Latencia de ida y vuelta del buffer"
        )
    )

    // Scroll con comportamiento suave
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0F),
                        Color(0xFF14141A)
                    )
                )
            )
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        // Encabezado con botón de retroceso y título elegante
        item {
            AnimatedContent(
                targetState = transitionState.currentState,
                transitionSpec = {
                    fadeIn() + slideInHorizontally(
                        initialOffsetX = { -it / 2 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
            ) { _ ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón de retroceso estilizado
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0x22FFFFFF),
                                        Color(0x11FFFFFF)
                                    )
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Text(
                        text = "CONFIGURACIÓN",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.W300,
                            letterSpacing = 2.5.sp,
                            fontSize = 20.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        textAlign = TextAlign.Center
                    )

                    // Espacio para balancear la fila
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        // Tarjeta de hardware: información del sistema
        item {
            AnimatedCard(
                delay = 0,
                transitionState = transitionState
            ) {
                Column {
                    // Título de sección con detalle decorativo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = AccentCyan.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "HARDWARE REAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.W600,
                                letterSpacing = 1.8.sp,
                                color = AccentCyan.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Micro indicador de estado
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (supportsHighRes) AccentEmerald
                                    else AccentAmber
                                )
                        )
                    }

                    // Lista de parámetros de hardware en columnas
                    hardwareItems.forEach { item ->
                        HardwareParameterRow(
                            icon = item.icon,
                            label = item.label,
                            value = item.value,
                            description = item.description,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    // Mensaje de calidad de audio
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!supportsHighRes) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = AccentAmber.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "192 kHz / 24-bit reales requieren DAC USB-C externo",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentAmber.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                )
                            }
                        }
                    } else {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = AccentEmerald.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Hardware compatible con alta resolución",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentEmerald.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tarjeta de información general (versión, autor, etc.)
        item {
            AnimatedCard(
                delay = 100,
                transitionState = transitionState
            ) {
                Column {
                    // Título
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = AccentCyan.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ACERCA DE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.W600,
                                letterSpacing = 1.8.sp,
                                color = AccentCyan.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Lista de elementos
                    sessionInfo.forEach { info ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    info.icon,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = info.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                            Text(
                                text = info.value,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.W400,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }

        // Espacio final para evitar que el contenido quede pegado al borde
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ======================================================================
// COMPONENTES REUTILIZABLES DE DISEÑO DE ÉLITE
// ======================================================================

/**
 * Tarjeta con efecto glassmorphism y animación de entrada en cascada.
 */
@Composable
private fun AnimatedCard(
    delay: Int,
    transitionState: MutableTransitionState<Boolean>,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (transitionState.currentState) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = delay,
            easing = FastOutSlowInEasing
        )
    )

    val animatedOffset by animateDpAsState(
        targetValue = if (transitionState.currentState) 0.dp else 20.dp,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = delay,
            easing = FastOutSlowInEasing
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = animatedOffset)
            .alpha(animatedAlpha)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1AFFFFFF)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x22FFFFFF),
                            Color(0x0AFFFFFF)
                        ),
                        startY = 0f,
                        endY = 1f
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            content()
        }
    }
}

/**
 * Fila que muestra un parámetro de hardware con icono, etiqueta, valor y descripción
 * emergente al pasar el cursor (simulado con un tooltip simple en UI móvil no aplica).
 * En móvil mostramos la descripción como subtítulo pequeño.
 */
@Composable
private fun HardwareParameterRow(
    icon: ImageVector,
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.W400,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            )
        }
        // Descripción como subtítulo sutil
        if (description.isNotEmpty()) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp
                ),
                modifier = Modifier.padding(start = 30.dp, top = 2.dp)
            )
        }
    }
}

/**
 * Clase de datos para información de sesión.
 */
private data class SessionInfo(
    val label: String,
    val value: String,
    val icon: ImageVector
)

/**
 * Clase de datos para elementos de hardware.
 */
private data class HardwareItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val description: String = ""
)

// ======================================================================
// NOTA: Se asume que DSPState expone las siguientes propiedades:
// - deviceSampleRateHz: Int
// - deviceFramesPerBuffer: Int
// - deviceSupportsHighRes: Boolean
// Si no existen en tu proyecto, créalas en DSPState.kt con valores dummy
// o extráelas mediante AudioManager.
// ======================================================================
