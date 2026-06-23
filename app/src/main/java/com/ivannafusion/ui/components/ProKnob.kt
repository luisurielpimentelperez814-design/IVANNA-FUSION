package com.ivannafusion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInputimport androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ProKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    label: String = "",
    unit: String = "",
    accentColor: Color = Color(0xFF00D9FF),
    modifier: Modifier = Modifier
) {
    var dragStartY by remember { mutableStateOf(0f) }
    var dragStartValue by remember { mutableStateOf(value) }
    
    Canvas(
        modifier = modifier
            .size(80.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartY = offset.y
                        dragStartValue = value
                    },
                    onDrag = { change, dragAmount ->
                        val deltaY = dragAmount.y
                        val sensitivity = (maxValue - minValue) / 200f
                        val newValue = (dragStartValue - deltaY * sensitivity).coerceIn(minValue, maxValue)
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        val size = this.size.minDimension
        val center = Offset(size / 2, size / 2)
        val radius = size / 2 - 8f
        
        // Track background
        drawArc(
            color = Color(0xFF334155),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
                // Value arc
        val sweepAngle = ((value - minValue) / (maxValue - minValue)) * 270f
        drawArc(
            color = accentColor,
            startAngle = 135f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
        
        // Indicator dot
        val angle = Math.toRadians(135.0 + sweepAngle)
        val dotX = center.x + (radius * cos(angle)).toFloat()
        val dotY = center.y + (radius * sin(angle)).toFloat()
        drawCircle(
            color = accentColor,
            radius = 6f,
            center = Offset(dotX, dotY)
        )
    }
}
