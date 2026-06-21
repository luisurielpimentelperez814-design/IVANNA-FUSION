package com.ivannafusion.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VUMeter(
    level: Float,
    modifier: Modifier = Modifier,
    peakHold: Boolean = true
) {
    var peakLevel by remember { mutableStateOf(level) }
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "vu"
    )
    
    LaunchedEffect(level) {
        if (level > peakLevel) peakLevel = level
        if (peakHold && peakLevel - level > 0.02f) {
            delay(1500)
            peakLevel = (peakLevel - 0.01f).coerceAtLeast(level)
        }
    }
    
    Canvas(modifier = modifier.width(12.dp).fillMaxHeight()) {
        val barWidth = size.width
        val barHeight = size.height
        val segmentHeight = barHeight / 40f
        val segmentGap = 1.dp.toPx()
        
        drawRect(
            color = Color(0xFF0F172A),
            size = Size(barWidth, barHeight)
        )
                val activeSegments = (animatedLevel * 40).toInt()
        for (i in 0 until 40) {
            val y = barHeight - (i + 1) * segmentHeight
            val color = when {
                i >= 36 -> SignalHot
                i >= 32 -> SignalWarm
                else -> SignalCool
            }
            val alpha = if (i < activeSegments) 1f else 0.15f
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(0f, y),
                size = Size(barWidth, segmentHeight - segmentGap)
            )
        }
        
        if (peakHold) {
            val peakY = barHeight - (peakLevel * 40) * segmentHeight
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, peakY),
                size = Size(barWidth, 2.dp.toPx())
            )
        }
    }
}

@Composable
fun SpectrumVisualizer(
    magnitudes: List<Float>,
    modifier: Modifier = Modifier,
    accentColor: Color = AccentCyan
) {
    val animatedMagnitudes = remember(magnitudes.size) {
        List(magnitudes.size) { Animatable(0f) }
    }
    
    LaunchedEffect(magnitudes) {
        magnitudes.forEachIndexed { index, mag ->
            if (index < animatedMagnitudes.size) {
                animatedMagnitudes[index].animateTo(
                    targetValue = mag.coerceIn(0f, 1f),
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
                )
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val barCount = magnitudes.size        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.7f
        val gap = (totalWidth / barCount) * 0.3f
        
        animatedMagnitudes.forEachIndexed { index, anim ->
            val x = index * (barWidth + gap)
            val barHeight = anim.value * size.height
            
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.3f)
                    ),
                    startY = size.height - barHeight,
                    endY = size.height
                ),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
fun CorrelationMeter(
    correlation: Float,
    modifier: Modifier = Modifier
) {
    val animatedCorr by animateFloatAsState(
        targetValue = correlation.coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = 0.7f),
        label = "corr"
    )
    
    Canvas(modifier = modifier.height(24.dp).fillMaxWidth()) {
        val centerY = size.height / 2f
        val width = size.width
        
        drawRect(
            color = Color(0xFF0F172A),
            size = Size(width, size.height)
        )
        
        drawLine(
            color = Color(0xFF334155),
            start = Offset(width / 2f, 0f),
            end = Offset(width / 2f, size.height),
            strokeWidth = 2f
        )        
        val x = (width / 2f) + (animatedCorr * width / 2f)
        val color = when {
            animatedCorr > 0.7f -> SignalCool
            animatedCorr < -0.3f -> SignalHot
            else -> SignalWarm
        }
        
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = Offset(x, centerY)
        )
    }
}
