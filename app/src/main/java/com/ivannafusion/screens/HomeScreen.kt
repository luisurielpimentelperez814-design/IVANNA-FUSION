package com.ivannafusion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ivannafusion.navigation.Routes

data class NavItem(val icon: ImageVector, val label: String, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var isEngineActive by remember { mutableStateOf(false) }
    val navItems = listOf(
        NavItem(Icons.Default.GraphicEq, "PF Engine", Routes.PF_ENGINE),
        NavItem(Icons.Default.LibraryMusic, "Presets", Routes.PRESETS),
        NavItem(Icons.Default.ThreeDRotation, "Espacial", Routes.SPATIAL),
        NavItem(Icons.Default.Equalizer, "Ecualizador", Routes.EQ),
        NavItem(Icons.Default.MonitorHeart, "Monitor", Routes.MONITOR),
        NavItem(Icons.Default.SmartToy, "IA", Routes.AI),
        NavItem(Icons.Default.Biotech, "Simbiosis", Routes.SIMBIOSIS),
        NavItem(Icons.Default.Settings, "Config", Routes.SETTINGS)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IVANNA FUSION", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isEngineActive) "MOTOR ACTIVO" else "MOTOR INACTIVO", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isEngineActive) Color(0xFF4CAF50) else Color(0xFF888888))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { isEngineActive = true }, modifier = Modifier.weight(1f), enabled = !isEngineActive, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Iniciar") }
                Button(onClick = { isEngineActive = false }, modifier = Modifier.weight(1f), enabled = isEngineActive, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text("Detener") }
            }
            Text("Módulos", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            for (row in navItems.chunked(2)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Card(modifier = Modifier.weight(1f).height(100.dp).clickable { navController.navigate(item.route) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(item.icon, contentDescription = item.label, tint = Color(0xFF00BCD4), modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(item.label, fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
