package com.ivannafusion.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager
import com.ivannafusion.ui.components.IVANNANavItem
import com.ivannafusion.ui.screens.*
import com.ivannafusion.ui.screens.PFEngineScreen
import com.ivannafusion.ui.theme.*
import androidx.compose.ui.graphics.Color

@Composable
fun AppNavigation(audioEngine: AudioEngine, presetManager: PresetManager) {
    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf("dashboard") }
    
    Scaffold(
        containerColor = BackgroundPrimary,
        bottomBar = {
            Surface(color = Color(0xFF1F1C14), tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IVANNANavItem(icon = Icons.Default.Dashboard, label = "INICIO", selected = currentRoute == "dashboard", onClick = { currentRoute = "dashboard"; navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } })
                    IVANNANavItem(icon = Icons.Default.GraphicEq, label = "EFECTOS", selected = currentRoute == "effects", onClick = { currentRoute = "effects"; navController.navigate("effects") })
                    IVANNANavItem(icon = Icons.Default.AutoAwesome, label = "IA", selected = currentRoute == "ai", onClick = { currentRoute = "ai"; navController.navigate("ai") })
                    IVANNANavItem(icon = Icons.Default.LibraryMusic, label = "PRESETS", selected = currentRoute == "presets", onClick = { currentRoute = "presets"; navController.navigate("presets") })
                    IVANNANavItem(icon = Icons.Default.Tune, label = "PF-ENG", selected = currentRoute == "pfengine", onClick = { currentRoute = "pfengine"; navController.navigate("pfengine") })
                    IVANNANavItem(icon = Icons.Default.Settings, label = "AJUSTES", selected = currentRoute == "settings", onClick = { currentRoute = "settings"; navController.navigate("settings") })
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(audioEngine = audioEngine, onNavigate = { route -> currentRoute = route; navController.navigate(route) }) }
            composable("effects") { EffectsScreen(audioEngine = audioEngine, onBack = { navController.popBackStack() }) }
            composable("ai") { AIScreen(audioEngine = audioEngine, onBack = { navController.popBackStack() }) }
            composable("presets") { PresetsScreen(audioEngine = audioEngine, presetManager = presetManager, onBack = { navController.popBackStack() }) }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable("pfengine") { PFEngineScreen(audioEngine = audioEngine, onBack = { navController.popBackStack() }) }
        }
    }
}
