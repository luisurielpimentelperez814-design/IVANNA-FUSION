package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(navController: NavController) {
    var cpuUsage by remember { mutableFloatStateOf(0f) }
    var latency by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            cpuUsage = (20..60).random().toFloat()
            latency = (3..8).random().toFloat()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitor", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CPU", fontWeight = FontWeight.Bold, color = Color(0xFF00BCD4))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = cpuUsage / 100f, modifier = Modifier.fillMaxWidth(), color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("%.1f%%".format(cpuUsage), color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Latencia", fontWeight = FontWeight.Bold, color = Color(0xFF00BCD4))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("%.1f ms".format(latency), fontSize = 24.sp, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}
