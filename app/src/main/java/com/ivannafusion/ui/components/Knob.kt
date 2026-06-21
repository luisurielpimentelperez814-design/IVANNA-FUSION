package com.ivannafusion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun IVANNAKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    label: String = "",
    unit: String = "",
    accentColor: Color = AccentCyan
) {
    val normalizedValue = (value - range.start) / (range.endInclusive - range.start)
    val animatedValue by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = tween(durationMillis = 150),
        label = "knob"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .size(size)            .pointerInput(range) {
                detectVerticalDragGestures(
                    onDragEnd = {},
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val sensitivity = (range.endInclusive - range.start) / 200f
                        val newValue = (value - dragAmount * sensitivity)
                            .coerceIn(range.start, range.endInclusive)
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 0.9f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.value * 0.08f
                val radius = (this.size.minDimension - strokeWidth) / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                
                drawArc(
                    color = Color(0xFF1F2937),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                val sweepAngle = animatedValue * 270f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(accentColor, accentColor.copy(alpha = 0.6f)),
                        center = center
                    ),
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                val angle = (135f + sweepAngle) * (PI / 180f).toFloat()
                val indicatorLength = radius * 0.75f
                val startX = center.x + cos(angle) * (radius * 0.4f)
                val startY = center.y + sin(angle) * (radius * 0.4f)
                val endX = center.x + cos(angle) * indicatorLength
                val endY = center.y + sin(angle) * indicatorLength
                                drawLine(
                    color = accentColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = strokeWidth * 0.6f,
                    cap = StrokeCap.Round
                )
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF334155), Color(0xFF1F2937)),
                        center = center,
                        radius = radius * 0.5f
                    ),
                    radius = radius * 0.5f,
                    center = center
                )
            }
            
            Text(
                text = String.format("%.1f%s", value, unit),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFF1F5F9),
                textAlign = TextAlign.Center
            )
        }
        
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
