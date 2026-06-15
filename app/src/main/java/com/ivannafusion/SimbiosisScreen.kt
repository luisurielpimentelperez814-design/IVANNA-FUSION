/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimbiosisScreen(navController: NavController) {
    val context = LocalContext.current
    var fusionLevel by remember { mutableFloatStateOf(0.5f) }
    var latencyUs by remember { mutableIntStateOf(0) }
    var phaseError by remember { mutableFloatStateOf(0f) }
    var isPredictionActive by remember { mutableStateOf(true) }
    var showGrandpaMode by remember { mutableStateOf(false) }

    val gestureDetector = remember { IVANNAGestureDetector(context) }

    LaunchedEffect(Unit) {
        gestureDetector.setCallbacks(
            wristTwist = { delta -> fusionLevel = (fusionLevel + delta * 0.1f).coerceIn(0f, 1f) },
            pinchRotate = { delta -> /* Ajustar ancho de banda */ },
            threeFingerSwipe = { navController.navigate("settings") },
            doubleTapLatency = { /* Reset contadores */ },
            twoFingerCircle = { active -> isPredictionActive = active }
        )
        gestureDetector.register()

        while (true) {
            latencyUs = AudioEngine.getLatencyMicros()
            phaseError = AudioEngine.getPhaseErrorRms()
            delay(16) // 60 Hz
        }
    }

    DisposableEffect(Unit) {
        onDispose { gestureDetector.unregister() }
    }

    if (showGrandpaMode) {
        GrandpaModeOverlay(
            onDismiss = { showGrandpaMode = false },
            onMagic = { fusionLevel = 1.0f },
            onSilence = { fusionLevel = 0f },
            onReset = { fusionLevel = 0.5f }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInteropFilter { event ->
                    gestureDetector.onTouchEvent(event)
                    true
                }
        ) {
            // Aura térmica en bordes
            val maxTemp = ThermalMonitor.getMaxTemperature()
            val thermalColor = when {
                maxTemp < 50 -> Color.Blue
                maxTemp < 70 -> Color.Cyan
                maxTemp < 85 -> Color.Magenta
                else -> Color.Red
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, thermalColor.copy(alpha = 0.15f)),
                            center = Offset.Infinite
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Dial circular de fusion_level
                FusionDial(
                    level = fusionLevel,
                    onLevelChange = { fusionLevel = it },
                    modifier = Modifier.size(280.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Línea de tiempo dual (pasado + predicción)
                DualTimeline(
                    isPredictionActive = isPredictionActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Anillo de latencia
                LatencyRing(
                    latencyUs = latencyUs,
                    modifier = Modifier.size(200.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${latencyUs} µs",
                    color = when {
                        latencyUs < 1000 -> Color.Green
                        latencyUs < 2000 -> Color.Yellow
                        latencyUs < 3000 -> Color(0xFFFFA500)
                        else -> Color.Red
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                // Destello de satisfacción si latencia "negativa" (predicción activa y baja)
                if (isPredictionActive && latencyUs < 1500) {
                    SatisfactionFlash()
                }

                Spacer(modifier = Modifier.weight(1f))

                // Navegación inferior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { navController.navigate("monitor") }) {
                        Text("MONITOR")
                    }
                    Button(onClick = { showGrandpaMode = true }) {
                        Text("MODO ABUELO")
                    }
                    Button(onClick = { navController.navigate("settings") }) {
                        Text("AJUSTES")
                    }
                }
            }
        }
    }
}

@Composable
fun FusionDial(level: Float, onLevelChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2 - 20

            // Fondo del dial
            drawCircle(
                color = Color.DarkGray,
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 20f)
            )

            // Arco de nivel
            val sweepAngle = level * 270f - 135f
            drawArc(
                color = when {
                    level < 0.33 -> Color.Blue
                    level < 0.66 -> Color.Cyan
                    else -> Color.Magenta
                },
                startAngle = -135f,
                sweepAngle = sweepAngle + 135f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 20f)
            )

            // Indicador
            val indicatorAngle = Math.toRadians((sweepAngle).toDouble())
            val indicatorX = centerX + (radius - 10) * cos(indicatorAngle).toFloat()
            val indicatorY = centerY + (radius - 10) * sin(indicatorAngle).toFloat()

            drawCircle(
                color = Color.White,
                radius = 15f,
                center = Offset(indicatorX, indicatorY)
            )
        }

        Text(
            text = "${(level * 100).toInt()}%",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DualTimeline(isPredictionActive: Boolean, modifier: Modifier = Modifier) {
    val realSamples = remember { FloatArray(256) { kotlin.math.sin(it * 0.1f) * 50 } }
    val predictedSamples = remember { FloatArray(256) { kotlin.math.sin((it + 256) * 0.1f) * 50 } }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Dibujar pasado (opaco)
        for (i in 0 until 255) {
            val x1 = i * width / 256
            val x2 = (i + 1) * width / 256
            val y1 = centerY - realSamples[i]
            val y2 = centerY - realSamples[i + 1]

            drawLine(
                color = Color.Green,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f
            )
        }

        // Dibujar predicción (semitransparente)
        if (isPredictionActive) {
            for (i in 0 until 255) {
                val x1 = (i + 256) * width / 512
                val x2 = (i + 257) * width / 512
                val y1 = centerY - predictedSamples[i]
                val y2 = centerY - predictedSamples[i + 1]

                drawLine(
                    color = Color.Green.copy(alpha = 0.4f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2f
                )
            }
        }

        // Línea divisoria
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(width / 2, 0f),
            end = Offset(width / 2, height),
            strokeWidth = 1f
        )
    }
}

@Composable
fun LatencyRing(latencyUs: Int, modifier: Modifier = Modifier) {
    val color = when {
        latencyUs < 1000 -> Color.Green
        latencyUs < 2000 -> Color.Yellow
        latencyUs < 3000 -> Color(0xFFFFA500)
        else -> Color.Red
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 10

        // Anillo cromático
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = radius,
            style = Stroke(width = 15f)
        )

        // Arco de progreso
        val progress = (latencyUs / 3000f).coerceIn(0f, 1f)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 15f)
        )
    }
}

@Composable
fun SatisfactionFlash() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = false
    }
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Green.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun GrandpaModeOverlay(
    onDismiss: () -> Unit,
    onMagic: () -> Unit,
    onSilence: () -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GrandpaButton("ENCENDER", Color.Green, onMagic)
            GrandpaButton("SILENCIO", Color.Yellow, onSilence)
            GrandpaButton("MAGIA", Color.Magenta, onMagic)
            GrandpaButton("RESET", Color.Red, onReset)
        }
    }
}

@Composable
fun GrandpaButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(120.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text = text, fontSize = 72.sp, fontWeight = FontWeight.Bold)
    }
}
