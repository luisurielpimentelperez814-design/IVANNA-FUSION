package com.ivannafusion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EffectsControlScreen() {
    var effectEnabled by remember { mutableStateOf(false) }
    var currentGain by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Control de Efectos IVANNA-FUSION",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                effectEnabled = !effectEnabled
                statusMessage = "Efecto: " + if (effectEnabled) "Activado" else "Desactivado"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (effectEnabled) Color(0xFF4CAF50) else Color(0xFF666666)
            )
        ) {
            Text(
                text = if (effectEnabled) "Desactivar Efecto" else "Activar Efecto",
                color = Color.White
            )        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Ganancia",
            fontSize = 18.sp,
            color = Color.White
        )

        Text(
            text = "Ganancia: ${currentGain.toInt()}dB",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )

        Slider(
            value = currentGain,
            onValueChange = { currentGain = it },
            valueRange = -20f..20f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                try {
                    IvannaNativeLib.setEnabled(effectEnabled)
                    IvannaNativeLib.eqSetGain(0, currentGain)
                    statusMessage = "Efecto aplicado: Gain=${currentGain.toInt()}dB"
                } catch (e: Exception) {
                    statusMessage = "Error: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Text(text = "Aplicar Efecto", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                fontSize = 14.sp,
                color = Color(0xFF4CAF50)
            )
        }    }
}
