package com.ivannafusion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.theme.*

/**
 * IVANNASlider — slider horizontal de precisión para controles DSP.
 *
 * Reemplaza IVANNAKnob: más superficie táctil, rango visual completo y
 * valor numérico siempre visible sin tener que leer la posición del puntero.
 *
 * @param value        Valor actual en las unidades reales del rango
 * @param onValueChange Callback con el nuevo valor en las mismas unidades
 * @param range        Rango real del parámetro (p.ej. -18f..18f para EQ en dB)
 * @param label        Etiqueta izquierda (nombre del parámetro)
 * @param labelWidth   Ancho fijo de la columna del label
 * @param valueText    Texto formateado del valor (derecha); si vacío usa "%.2f"
 * @param valueWidth   Ancho fijo de la columna del valor
 * @param accentColor  Color del thumb y del track activo
 * @param enabled      Permite/bloquea la interacción
 * @param compact      true = altura reducida para listas densas (EQ de 10 bandas)
 */
@Composable
fun IVANNASlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    label: String = "",
    labelWidth: Dp = 64.dp,
    valueText: String = "",
    valueWidth: Dp = 72.dp,
    accentColor: Color = AccentCyan,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 36.dp else 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label.isNotEmpty()) {
            Text(
                text  = label,
                style = if (compact) MaterialTheme.typography.labelSmall
                        else         MaterialTheme.typography.labelMedium,
                color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f),
                modifier  = Modifier.width(labelWidth),
                maxLines  = 1
            )
        }

        Slider(
            value       = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { if (enabled) onValueChange(it) },
            valueRange  = range,
            enabled     = enabled,
            colors      = SliderDefaults.colors(
                thumbColor              = if (enabled) accentColor else accentColor.copy(alpha = 0.35f),
                activeTrackColor        = if (enabled) accentColor else accentColor.copy(alpha = 0.2f),
                inactiveTrackColor      = Color(0xFF1E293B),
                disabledThumbColor      = accentColor.copy(alpha = 0.3f),
                disabledActiveTrackColor= accentColor.copy(alpha = 0.15f),
                disabledInactiveTrackColor = Color(0xFF0F172A)
            ),
            modifier = Modifier.weight(1f)
        )

        val display = valueText.ifEmpty { "%.2f".format(value) }
        Text(
            text      = display,
            style     = if (compact) MaterialTheme.typography.labelSmall
                        else         MaterialTheme.typography.labelMedium,
            color     = if (enabled) accentColor else accentColor.copy(alpha = 0.4f),
            textAlign = TextAlign.End,
            modifier  = Modifier.width(valueWidth),
            maxLines  = 1
        )
    }
}
