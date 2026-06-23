/*
 * IVANNA FUSION — VUNeedle
 * VU meter de aguja analógica real: Canvas, escala calibrada, zonas de color.
 * Elemento firma del rediseño visual v2.
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */
package com.ivannafusion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivannafusion.ui.theme.*
import kotlin.math.*

/**
 * VU meter de aguja analógica.
 *
 * @param levelDb  nivel en dBFS (-60 .. 0). Se mapea a la escala VU estándar.
 * @param modifier
 */
@Composable
fun VUNeedleMeter(
    levelDb: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp)
) {
    // dBFS → posición normalizada 0..1 en la escala del VU
    // 0 VU = -18 dBFS (estándar broadcast); +3 VU = -15 dBFS
    val vu        = (levelDb + 18f) / 21f   // -18 dBFS = 0 VU (centro), +3 VU = 1.0
    val target    = vu.coerceIn(-0.4f, 1.2f) // permite un poco de sobre-escala visual

    // Aguja con inercia de VU real: ataque rápido, caída lenta
    val animated by animateFloatAsState(
        targetValue    = target,
        animationSpec  = spring(dampingRatio = 0.45f, stiffness = 80f),
        label          = "vu_needle"
    )

    val measText = rememberTextMeasurer()

    Canvas(modifier = modifier.background(Color(0xFF0F0D08))) {
        drawVUFace(measText, animated)
    }
}

private fun DrawScope.drawVUFace(measurer: TextMeasurer, normalizedLevel: Float) {
    val w  = size.width
    val h  = size.height

    // ── Pivot: parte inferior central ───────────────────────────────────────
    val pivotX   = w / 2f
    val pivotY   = h * 0.88f
    val radius   = h * 0.80f

    // Barrido: 210° (izq) → 330° (der), 0° = arriba (12 en reloj)
    val startDeg = 210f
    val sweepDeg = 120f

    // ── Fondo con textura sutíl de grano ─────────────────────────────────────
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A1810), Color(0xFF0F0D08)),
            center = Offset(pivotX, pivotY - radius * 0.4f),
            radius = radius * 1.5f
        )
    )

    // ── Arco de escala ────────────────────────────────────────────────────────
    // Zona verde: startDeg .. (inicio zona roja)
    val redStartNorm = 0.67f                       // 0 VU
    val redStartDeg  = startDeg + sweepDeg * redStartNorm
    drawArc(
        color      = Color(0xFF2A3A22),
        startAngle = startDeg - 90f,
        sweepAngle = sweepDeg * redStartNorm,
        useCenter  = false,
        topLeft    = Offset(pivotX - radius, pivotY - radius),
        size       = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style      = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Butt)
    )
    drawArc(
        color      = Color(0xFF3A1818),
        startAngle = redStartDeg - 90f,
        sweepAngle = sweepDeg * (1f - redStartNorm),
        useCenter  = false,
        topLeft    = Offset(pivotX - radius, pivotY - radius),
        size       = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style      = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Butt)
    )

    // ── Marcas de escala calibradas ───────────────────────────────────────────
    // (VU, normalized_position, major)
    val marks = listOf(
        Triple("-20", 0.00f, true),
        Triple("-10", 0.28f, true),
        Triple("-7",  0.40f, false),
        Triple("-5",  0.49f, false),
        Triple("-3",  0.58f, false),
        Triple("0",   0.67f, true),
        Triple("+1",  0.78f, false),
        Triple("+2",  0.89f, false),
        Triple("+3",  1.00f, true)
    )

    for ((label, norm, major) in marks) {
        val deg = startDeg + sweepDeg * norm
        val rad = Math.toRadians(deg.toDouble())
        val isRed   = norm >= 0.67f
        val tickColor = if (isRed) Color(0xFFB85540) else Color(0xFF9A9378)

        val outerR = radius - 2.dp.toPx()
        val innerR = outerR - if (major) 14.dp.toPx() else 8.dp.toPx()

        val ox = pivotX + (outerR * cos(rad)).toFloat()
        val oy = pivotY + (outerR * sin(rad)).toFloat()
        val ix = pivotX + (innerR * cos(rad)).toFloat()
        val iy = pivotY + (innerR * sin(rad)).toFloat()

        drawLine(
            color = tickColor,
            start = Offset(ox, oy),
            end   = Offset(ix, iy),
            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
        )

        if (major) {
            val textR = innerR - 14.dp.toPx()
            val tx    = pivotX + (textR * cos(rad)).toFloat()
            val ty    = pivotY + (textR * sin(rad)).toFloat()
            val style = TextStyle(
                fontSize     = 8.sp,
                fontFamily   = FontFamily.Monospace,
                fontWeight   = FontWeight.Bold,
                color        = if (isRed) Color(0xFFB85540) else Color(0xFF6B6554)
            )
            val measured = measurer.measure(label, style)
            drawText(
                measurer,
                text      = label,
                topLeft   = Offset(tx - measured.size.width / 2f, ty - measured.size.height / 2f),
                style     = style
            )
        }
    }

    // ── Etiqueta "VU" centrada ────────────────────────────────────────────────
    val vuStyle = TextStyle(
        fontSize   = 11.sp, fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black, color = Color(0xFFC9A85C), letterSpacing = 4.sp
    )
    val vuMeasured = measurer.measure("VU", vuStyle)
    drawText(
        measurer, text = "VU",
        topLeft = Offset(pivotX - vuMeasured.size.width / 2f, pivotY - radius * 0.35f),
        style = vuStyle
    )

    // ── Arco de nivel activo ──────────────────────────────────────────────────
    val levelNorm  = normalizedLevel.coerceIn(0f, 1f)
    val levelColor = if (levelNorm < redStartNorm) Color(0xFF6E8467) else Color(0xFFB85540)
    if (levelNorm > 0.01f) {
        drawArc(
            color      = levelColor.copy(alpha = 0.6f),
            startAngle = startDeg - 90f,
            sweepAngle = sweepDeg * levelNorm,
            useCenter  = false,
            topLeft    = Offset(pivotX - radius + 8.dp.toPx(), pivotY - radius + 8.dp.toPx()),
            size       = androidx.compose.ui.geometry.Size(
                (radius - 8.dp.toPx()) * 2, (radius - 8.dp.toPx()) * 2
            ),
            style      = Stroke(width = 3.dp.toPx())
        )
    }

    // ── Aguja ─────────────────────────────────────────────────────────────────
    val needleDeg = startDeg + sweepDeg * normalizedLevel.coerceIn(-0.05f, 1.05f)
    val nRad      = Math.toRadians(needleDeg.toDouble())
    val needleLen = radius * 0.88f
    val counterLen= radius * 0.12f

    // Sombra de aguja
    drawLine(
        color       = Color.Black.copy(alpha = 0.6f),
        start       = Offset(
            pivotX + (counterLen * cos(nRad + PI)).toFloat() + 1f,
            pivotY + (counterLen * sin(nRad + PI)).toFloat() + 1f
        ),
        end         = Offset(
            pivotX + (needleLen * cos(nRad)).toFloat() + 1f,
            pivotY + (needleLen * sin(nRad)).toFloat() + 1f
        ),
        strokeWidth = 2.5f
    )
    // Aguja principal — filamento metálico
    drawLine(
        brush       = Brush.linearGradient(
            colors  = listOf(Color(0xFF8A8070), Color(0xFFE5DFC8), Color(0xFF8A8070)),
            start   = Offset(pivotX, pivotY),
            end     = Offset(pivotX + (needleLen * cos(nRad)).toFloat(),
                             pivotY + (needleLen * sin(nRad)).toFloat())
        ),
        start       = Offset(
            pivotX + (counterLen * cos(nRad + PI)).toFloat(),
            pivotY + (counterLen * sin(nRad + PI)).toFloat()
        ),
        end         = Offset(
            pivotX + (needleLen * cos(nRad)).toFloat(),
            pivotY + (needleLen * sin(nRad)).toFloat()
        ),
        strokeWidth = 1.8f,
        cap         = StrokeCap.Round
    )
    // Contrapeso (parte trasera de la aguja)
    drawLine(
        color       = Color(0xFFC9A85C),
        start       = Offset(pivotX, pivotY),
        end         = Offset(
            pivotX + (counterLen * cos(nRad + PI)).toFloat(),
            pivotY + (counterLen * sin(nRad + PI)).toFloat()
        ),
        strokeWidth = 3f,
        cap         = StrokeCap.Round
    )
    // Pivot (tornillo del eje)
    drawCircle(
        brush  = Brush.radialGradient(
            listOf(Color(0xFFE5DFC8), Color(0xFF8A7A60)),
            center = Offset(pivotX, pivotY), radius = 5.dp.toPx()
        ),
        radius = 5.dp.toPx(),
        center = Offset(pivotX, pivotY)
    )
    drawCircle(
        color  = Color(0xFF1A1710),
        radius = 2.dp.toPx(),
        center = Offset(pivotX, pivotY)
    )

    // ── Bisel exterior ────────────────────────────────────────────────────────
    drawRect(
        brush = Brush.linearGradient(
            listOf(Color(0x303A3626), Color.Transparent),
            start = Offset(0f, 0f), end = Offset(0f, h * 0.4f)
        )
    )
}
