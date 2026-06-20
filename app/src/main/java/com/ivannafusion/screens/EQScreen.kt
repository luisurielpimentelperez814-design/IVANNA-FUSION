package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
fun EQScreen(navController: NavController) {
    val bands = listOf("60Hz", "250Hz", "1kHz", "4kHz", "12kHz")
    var gains by remember { mutableStateOf(List(5) { 0f }) }
    var enabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ecualizador", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ecualizador", fontSize = 16.sp, color = Color.White)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                bands.forEachIndexed { index, band ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.0fdB".format(gains[index]), fontSize = 12.sp, color = Color(0xFF00BCD4))
                        Slider(value = gains[index], onValueChange = { value -> gains = gains.toMutableList().also { it[index] = value } }, valueRange = -12f..12f, modifier = Modifier.height(150.dp), enabled = enabled)
                        Text(band, fontSize = 10.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { gains = List(5) { 0f } }, modifier = Modifier.fillMaxWidth(), enabled = enabled) { Text("Resetear") }
        }
    }
}
