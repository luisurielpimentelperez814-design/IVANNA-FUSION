package com.ivannafusion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
@Composable
fun MoreMenuScreen(navController: NavController) {
    val items = listOf(
        Triple("🎛️", "Compressor", "Multibanda 4 bandas") to "compressor",
        Triple("🔊", "Convolver", "6 IRs sintetizados") to "convolver",
        Triple("🎧", "AutoEQ", "14 auriculares") to "autoeq",
        Triple("📊", "Analyzer", "Spectrogram + LUFS") to "analyzer",
        Triple("💾", "Presets", "16 presets + export") to "presets",
        Triple("⚙️", "Settings", "Configuración") to "settings"
    )
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("⋮ Más Opciones", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        items.forEach { item ->
            val ((icon, title, desc), route) = item
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { navController.navigate(route) },
                colors = CardDefaults.cardColors(Color(0xFF1E1E1E))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 32.sp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(desc, color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
