package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PFEngineScreen(navController: NavController) {
    var evolutionRunning by remember { mutableStateOf(false) }
    var mutationRate by remember { mutableFloatStateOf(0.01f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PF Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Estado: ${if (evolutionRunning) "Evolucionando" else "Detenido"}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (evolutionRunning) Color(0xFF4CAF50) else Color(0xFF888888))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { evolutionRunning = !evolutionRunning }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (evolutionRunning) Color(0xFFF44336) else Color(0xFF4CAF50))) {
                Text(if (evolutionRunning) "Detener Evolución" else "Iniciar Evolución")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Tasa de Mutación: %.3f".format(mutationRate), color = Color.White)
            Slider(value = mutationRate, onValueChange = { mutationRate = it }, valueRange = 0.001f..0.1f)
        }
    }
}
