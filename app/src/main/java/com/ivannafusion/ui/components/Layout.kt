package com.ivannafusion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.theme.*

@Composable
fun IVANNAHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundSecondary, BackgroundSecondary.copy(alpha = 0.8f))
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
        trailing()
    }}

@Composable
fun IVANNACard(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundTertiary)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
        }
        content()
    }
}

@Composable
fun IVANNANavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
        label = "nav"
    )
    val contentColor = if (selected) AccentCyan else TextSecondary
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}
@Composable
fun StatusChip(
    text: String,
    color: Color = AccentCyan,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
