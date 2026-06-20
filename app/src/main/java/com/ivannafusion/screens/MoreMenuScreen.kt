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

data class MenuItem(val icon: String, val title: String, val desc: String, val route: String)

@Composable
fun MoreMenuScreen(navController: NavController) {
    val items = listOf(
        MenuItem("🎛️", "Compressor", "Multibanda 4 bandas", "compressor"),
        MenuItem("🔊", "Convolver", "6 IRs sintetizados", "convolver"),
        MenuItem("🎧", "AutoEQ", "13 auriculares", "autoeq"),
        MenuItem("📊", "Analyzer", "Spectrogram + LUFS", "analyzer"),
        MenuItem("💾", "Presets", "8 presets profesionales", "presets"),
        MenuItem("⚙️", "Settings", "Configuracion", "settings")
    )
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("Menu Completo", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { navController.navigate(item.route) },
                colors = CardDefaults.cardColors(Color(0xFF1E1E1E))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.icon, fontSize = 32.sp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(item.desc, color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
