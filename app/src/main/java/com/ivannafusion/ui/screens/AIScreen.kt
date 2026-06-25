package com.ivannafusion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivannafusion.DSPState

@Composable
fun AIScreen(onBack: () -> Unit) {
    var aiEnabled by remember { mutableStateOf(DSPState.aiEnabled) }
    var aiAutoAdapt by remember { mutableStateOf(DSPState.aiAutoAdapt) }
    var aiSensitivity by remember { mutableFloatStateOf(DSPState.aiSensitivity) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Screen", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Text("AI Enabled")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = aiEnabled,
                onCheckedChange = { aiEnabled = it; DSPState.aiEnabled = it }
            )
        }
        Row {
            Text("Auto Adapt")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = aiAutoAdapt,
                onCheckedChange = { aiAutoAdapt = it; DSPState.aiAutoAdapt = it }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sensitivity: ${"%.2f".format(aiSensitivity)}")
        Slider(
            value = aiSensitivity,
            onValueChange = { aiSensitivity = it; DSPState.aiSensitivity = it },
            valueRange = 0f..1f
        )
        // Botón de guardado simulado
        Button(onClick = { /* guardar */ }) {
            Text("Save AI Settings")
        }
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
