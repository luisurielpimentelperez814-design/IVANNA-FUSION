package com.ivannafusion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.theme.*

@Composable
fun IVANNAFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    label: String = "",
    accentColor: Color = AccentCyan
) {
    val normalizedValue = (value - range.start) / (range.endInclusive - range.start)
    val animatedValue by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "fader"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(48.dp)
    ) {
        if (label.isNotEmpty()) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                .pointerInput(range) {
                    detectVerticalDragGestures(
                        onDragEnd = {},
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val sensitivity = (range.endInclusive - range.start) / 200f
                            val newValue = (value + dragAmount * sensitivity)
                                .coerceIn(range.start, range.endInclusive)
                            onValueChange(newValue)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 4.dp.toPx()
                val trackX = (size.width - trackWidth) / 2f
                
                drawRect(
                    color = Color(0xFF1F2937),
                    topLeft = Offset(trackX, 0f),
                    size = Size(trackWidth, size.height)
                )
                
                val fillHeight = animatedValue * size.height
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor, accentColor.copy(alpha = 0.4f)),
                        startY = size.height - fillHeight,
                        endY = size.height
                    ),
                    topLeft = Offset(trackX, size.height - fillHeight),
                    size = Size(trackWidth, fillHeight)
                )
                
                val thumbY = size.height - fillHeight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFE2E8F0)),
                        center = Offset(size.width / 2f, thumbY),
                        radius = 12.dp.toPx()                    ),
                    radius = 10.dp.toPx(),
                    center = Offset(size.width / 2f, thumbY)
                )
                drawCircle(
                    color = accentColor,
                    radius = 2.dp.toPx(),
                    center = Offset(size.width / 2f, thumbY)
                )
            }
        }
        
        Spacer(Modifier.height(4.dp))
        Text(text = String.format("%.1f", value), style = MaterialTheme.typography.labelMedium, color = TextAccent)
    }
}

@Composable
fun IVANNAToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) AccentCyan else Color(0xFF334155),
        animationSpec = spring(),
        label = "toggle"
    )
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(end = 8.dp))
        }
        
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .clickable { onCheckedChange(!checked) }
        ) {
            val offsetX by animateFloatAsState(
                targetValue = if (checked) 20f else 0f,
                animationSpec = spring(),
                label = "thumb"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(offsetX + 12.dp.toPx(), size.height / 2f)
                )
            }
        }
    }
}

@Composable
fun IVANNAButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AccentCyan,
    enabled: Boolean = true
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) accent.copy(alpha = 0.15f) else Color(0xFF1F2937),
        label = "btn"
    )
    
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, if (enabled) accent else BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else TextTertiary
        )
    }
}
