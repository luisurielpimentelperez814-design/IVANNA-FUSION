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
import androidx.compose.ui.draw.shadow
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
fun MasterSpectrumVisualizer(
    modifier: Modifier = Modifier,
    amplitudes: List<Float> = List(64) { (sin(it * 0.3f) + 1f) / 2f },
    gradientColors: List<Color> = MasterGradient,
    backgroundColor: Color = Carbon
) {
    val infiniteTransition = rememberInfiniteTransition()
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    
    Canvas(modifier = modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))
        .background(backgroundColor).border(1.dp, PrecisionCyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        .shadow(8.dp, RoundedCornerShape(16.dp))) {
        val barCount = amplitudes.size
        val barWidth = size.width / (barCount * 1.4f)
        val spacing = barWidth * 0.4f
        val maxBarHeight = size.height * 0.85f
        val gradient = Brush.verticalGradient(gradientColors)

        amplitudes.forEachIndexed { index, amplitude ->
            val animatedAmplitude = amplitude * (0.85f + 0.15f * sin(animatedPhase + index * 0.2f).toFloat())
            val barHeight = animatedAmplitude * maxBarHeight
            val x = index * (barWidth + spacing) + spacing / 2
            val y = size.height - barHeight
            drawRoundRect(brush = gradient, topLeft = Offset(x, y), size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2), alpha = 0.9f)
        }
    }
}

@Composable
fun MasterKnob(
    value: Float, onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier, size: Dp = 80.dp,
    label: String = "", accentColor: Color = PrecisionCyan
) {
    var dragStartValue by remember { mutableStateOf(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.pointerInput(value) {
        detectDragGestures(onDragStart = { dragStartValue = value },
            onDrag = { _, dragAmount -> onValueChange((dragStartValue - dragAmount.y / 200f).coerceIn(0f, 1f)) })
    }) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.toPx() * 0.08f
                val radius = (size.toPx() - strokeWidth) / 2
                val center = Offset(size.toPx() / 2, size.toPx() / 2)
                drawCircle(color = Graphite, radius = radius, center = center, style = Stroke(strokeWidth))
                val startAngle = 135f
                drawArc(color = Titanium, startAngle = startAngle, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2), size = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round))
                drawArc(color = accentColor, startAngle = startAngle, sweepAngle = (value * 270f), useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2), size = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round))
                val indicatorAngle = ((startAngle + value * 270f) * PI / 180).toFloat()
                drawLine(color = accentColor, start = center,
                    end = Offset(center.x + cos(indicatorAngle) * radius * 0.7f, center.y + sin(indicatorAngle) * radius * 0.7f),
                    strokeWidth = size.toPx() * 0.04f, cap = StrokeCap.Round)
                drawCircle(color = accentColor, radius = size.toPx() * 0.06f, center = center)
            }
            Text(text = "${(value * 100).toInt()}", style = MaterialTheme.typography.labelLarge, color = Platinum, fontWeight = FontWeight.Bold)
        }
        if (label.isNotEmpty()) Text(text = label, style = MaterialTheme.typography.labelMedium, color = Silver, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MasterCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Graphite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = PrecisionCyan,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            content()
        }
    }
}
