package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimbiosisScreen(navController: NavController) {
    var symbiosisActive by remember { mutableStateOf(false) }
    var harmonyLevel by remember { mutableFloatStateOf(0.5f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simbiosis", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF9C27B0))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Modo Simbiosis", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (symbiosisActive) "ACTIVO" else "INACTIVO", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (symbiosisActive) Color(0xFF4CAF50) else Color(0xFF888888))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Armonía", color = Color.White)
            Slider(value = harmonyLevel, onValueChange = { harmonyLevel = it }, enabled = symbiosisActive)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { symbiosisActive = !symbiosisActive }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (symbiosisActive) Color(0xFFF44336) else Color(0xFF4CAF50))) {
                Text(if (symbiosisActive) "Desactivar Simbiosis" else "Activar Simbiosis")
            }
        }
    }
}
