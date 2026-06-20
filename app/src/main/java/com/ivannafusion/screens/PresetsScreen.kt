package com.ivannafusion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
fun PresetsScreen(navController: NavController) {
    val presets = listOf("Flat (Reference)", "Mastering", "Dolby Cinema", "Bass Head", "Vocal Clarity", "Gaming")
    var selectedPreset by remember { mutableStateOf("Flat (Reference)") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presets", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color(0xFF00BCD4))
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { selectedPreset = preset }, colors = CardDefaults.cardColors(containerColor = if (preset == selectedPreset) Color(0xFF00BCD4) else Color(0xFF2A2A2A))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(preset, fontSize = 16.sp, color = if (preset == selectedPreset) Color.Black else Color.White)
                        if (preset == selectedPreset) Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                    }
                }
            }
        }
    }
}
