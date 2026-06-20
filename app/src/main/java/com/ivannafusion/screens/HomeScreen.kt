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
import com.ivannafusion.IvannaNativeLib

data class QuickItem(val icon: String, val label: String, val route: String)

@Composable
fun QuickButton(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp).clickable { onClick() }) {
        Card(
            colors = CardDefaults.cardColors(Color(0xFF2A2A2A)),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(icon, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFAAAAAA), fontSize = 10.sp)
    }
}

@Composable
fun HomeScreen(navController: NavController) {
    var effectEnabled by remember { mutableStateOf(IvannaNativeLib.isEnabled()) }
    var currentPreset by remember { mutableStateOf("Flat (Reference)") }
    val presets = listOf(
        "Flat (Reference)", "Mastering", "Dolby Cinema",
        "Sony DSEE", "Bass Head", "Vocal Clarity", "Gaming", "Late Night"
    )

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("IVANNA-FUSION", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Switch(checked = effectEnabled, onCheckedChange = { v -> effectEnabled = v; IvannaNativeLib.setEnabled(v) },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CAF50)))
        }        Text(if (effectEnabled) "ACTIVO" else "INACTIVO",
            color = if (effectEnabled) Color(0xFF4CAF50) else Color(0xFF888888), fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 20.dp))

        Text("Preset Activo", color = Color(0xFFAAAAAA), fontSize = 12.sp)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(value = currentPreset, onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = Color.White, focusedTextColor = Color.White,
                    unfocusedBorderColor = Color(0xFF444444), focusedBorderColor = Color(0xFF4CAF50)),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                presets.forEach { p ->
                    DropdownMenuItem(text = { Text(p) }, onClick = {
                        currentPreset = p
                        expanded = false
                        IvannaNativeLib.setPreset(presets.indexOf(p))
                    })
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Acceso Rapido", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        val row1 = listOf(
            QuickItem("🧠", "AI", "ai"),
            QuickItem("🎚️", "EQ", "eq"),
            QuickItem("🌌", "Spatial", "spatial"),
            QuickItem("🎛️", "Comp", "compressor")
        )
        val row2 = listOf(
            QuickItem("🔊", "Reverb", "convolver"),
            QuickItem("🎧", "AutoEQ", "autoeq"),
            QuickItem("📊", "Meter", "analyzer"),
            QuickItem("💾", "Presets", "presets")
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row1.forEach { item ->
                QuickButton(item.icon, item.label) { navController.navigate(item.route) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row2.forEach { item ->                QuickButton(item.icon, item.label) { navController.navigate(item.route) }
            }
        }
    }
}
