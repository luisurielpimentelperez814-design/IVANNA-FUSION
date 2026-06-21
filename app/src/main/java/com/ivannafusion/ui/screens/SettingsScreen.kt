package com.ivannafusion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivannafusion.ui.components.*
import com.ivannafusion.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary).verticalScroll(rememberScrollState())) {
        IVANNAHeader(title = "AJUSTES", subtitle = "Configuración del sistema") {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = AccentCyan) }
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("AUDIO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            SettingRow(label = "Sample Rate", value = "48000 Hz")
            SettingRow(label = "Bit Depth", value = "16 bit")
            SettingRow(label = "Buffer", value = "5000 μs")
        }
        
        IVANNACard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("INFORMACIÓN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            SettingRow(label = "Versión", value = "2.1.0")
            SettingRow(label = "Build", value = "2025.06.21")
            SettingRow(label = "Autor", value = "Luis Uriel Pimentel")
        }
        
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = AccentCyan)
    }
}
