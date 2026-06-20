package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
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
fun AIScreen(navController: NavController) {
    var modelLoaded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asistente IA", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF00BCD4))
            Spacer(modifier = Modifier.height(16.dp))
            Text("IVANNA AI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Estado: ${if (modelLoaded) "Modelo Cargado" else "Sin Modelo"}", fontWeight = FontWeight.Bold, color = if (modelLoaded) Color(0xFF4CAF50) else Color(0xFF888888))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { modelLoaded = true }, modifier = Modifier.fillMaxWidth(), enabled = !modelLoaded) { Text("Cargar Modelo IA") }
        }
    }
}
