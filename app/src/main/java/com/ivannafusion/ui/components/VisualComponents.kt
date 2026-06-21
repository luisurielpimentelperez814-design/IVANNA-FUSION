package com.ivannafusion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.theme.*
import kotlin.math.*

@Composable
fun SpectrumVisualizer(
    modifier: Modifier = Modifier,
    amplitudes: List<Float> = List(32) { (sin(it * 0.3f) + 1f) / 2f },
    barColor: Color = NeonCyan,
    backgroundColor: Color = DarkSurface
) {
    val infiniteTransition = rememberInfiniteTransition()
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, barColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        val barCount = amplitudes.size
        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(color = Color.White.copy(alpha = 0.05f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
        }
        
        amplitudes.forEachIndexed { index, amplitude ->
            val x = index * (barWidth + spacing)
            val animatedAmp = amplitude * (0.8f + 0.2f * sin(animatedPhase + index * 0.5f).toFloat())
            val barHeight = animatedAmp * size.height * 0.9f
            
            val glowGradient = Brush.verticalGradient(colors = listOf(barColor.copy(alpha = 0.8f), barColor.copy(alpha = 0.3f), barColor.copy(alpha = 0.1f)))
            
            drawRoundRect(brush = glowGradient, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(barWidth / 2))
            drawRoundRect(color = barColor, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(barWidth / 2))
            drawCircle(color = Color.White, radius = barWidth / 3, center = Offset(x + barWidth / 2, size.height - barHeight))
        }
    }
}

@Composable
fun VUMeter(
    modifier: Modifier = Modifier,
    level: Float = 0f,
    label: String = "VU",
    peakColor: Color = NeonRed,
    normalColor: Color = NeonGreen
) {
    val clampedLevel = level.coerceIn(-60f, 0f)
    val normalizedLevel = (clampedLevel + 60f) / 60f
    
    val infiniteTransition = rememberInfiniteTransition()
    val needleAngle by infiniteTransition.animateFloat(
        initialValue = -45f,
        targetValue = -45f + normalizedLevel * 90f,
        animationSpec = tween(150, easing = EaseOutCubic)
    )
    
    Canvas(
        modifier = modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(colors = listOf(DarkSurface, DeepBlack)))
            .border(2.dp, NeonCyan.copy(alpha = 0.3f), CircleShape)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 8f
        
        drawArc(color = Color.White.copy(alpha = 0.1f), startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(centerX - radius, centerY - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = 4f))
        drawArc(color = normalColor.copy(alpha = 0.3f), startAngle = 180f, sweepAngle = 120f, useCenter = false, topLeft = Offset(centerX - radius, centerY - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = 12f))
        drawArc(color = peakColor.copy(alpha = 0.3f), startAngle = 300f, sweepAngle = 60f, useCenter = false, topLeft = Offset(centerX - radius, centerY - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = 12f))
        
        for (i in 0..10) {
            val angle = 180f + i * 18f
            val rad = Math.toRadians(angle.toDouble())
            val innerR = radius * 0.75f
            val outerR = radius * 0.9f
            drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(centerX + innerR * cos(rad).toFloat(), centerY + innerR * sin(rad).toFloat()), end = Offset(centerX + outerR * cos(rad).toFloat(), centerY + outerR * sin(rad).toFloat()), strokeWidth = 2f)
        }
        
        val needleRad = Math.toRadians((needleAngle + 225).toDouble())
        drawLine(color = peakColor, start = Offset(centerX, centerY), end = Offset(centerX + radius * 0.85f * cos(needleRad).toFloat(), centerY + radius * 0.85f * sin(needleRad).toFloat()), strokeWidth = 3f)
        drawCircle(color = LightSurface, radius = 8f, center = Offset(centerX, centerY))
    }
}

@Composable
fun ProfessionalKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    label: String = "",
    unit: String = "",
    size: Dp = 80.dp,
    accentColor: Color = NeonCyan
) {
    var dragStartY by remember { mutableStateOf(0f) }
    var dragStartValue by remember { mutableStateOf(value) }
    val normalizedValue = (value - minValue) / (maxValue - minValue)
    
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
        }
        
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragStartY = it.y; dragStartValue = value },
                        onDrag = { change, dragAmount ->
                            val dragDelta = dragStartY - change.position.y
                            val range = maxValue - minValue
                            val newValue = (dragStartValue + (dragDelta / 200f) * range).coerceIn(minValue, maxValue)
                            onValueChange(newValue)
                        }
                    )
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(colors = listOf(LightSurface, DarkSurface)))
                .border(3.dp, accentColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.toPx() / 2
                val centerY = size.toPx() / 2
                val indicatorRadius = size.toPx() / 2 - 12f
                val rotation = -135f + normalizedValue * 270f
                val rad = Math.toRadians((rotation - 90).toDouble())
                
                drawLine(color = accentColor, start = Offset(centerX, centerY), end = Offset(centerX + indicatorRadius * cos(rad).toFloat(), centerY + indicatorRadius * sin(rad).toFloat()), strokeWidth = 4f)
                drawArc(color = accentColor.copy(alpha = 0.3f), startAngle = -225f, sweepAngle = normalizedValue * 270f, useCenter = false, topLeft = Offset(8f, 8f), size = Size(size.toPx() - 16f, size.toPx() - 16f), style = Stroke(width = 6f))
            }
            
            Text(text = String.format("%.1f%s", value, unit), style = MaterialTheme.typography.bodyMedium, color = accentColor, fontWeight = FontWeight.Bold)
        }
        
        Text(text = "${String.format("%.0f%%", normalizedValue * 100)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NeonCyan.copy(alpha = 0.3f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MediumSurface.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun NeonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    color: Color = NeonCyan,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).border(2.dp, color.copy(alpha = if (enabled) 0.8f else 0.3f), RoundedCornerShape(12.dp)),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = if (enabled) 0.2f else 0.1f),
            contentColor = color,
            disabledContainerColor = color.copy(alpha = 0.1f),
            disabledContentColor = color.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LEDIndicator(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NeonGreen,
    size: Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
    )
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) color.copy(alpha = if (glowAlpha > 0.75f) 1f else 0.6f) else Color.Gray.copy(alpha = 0.3f))
            .border(1.dp, if (active) color else Color.Gray, CircleShape)
    )
}
