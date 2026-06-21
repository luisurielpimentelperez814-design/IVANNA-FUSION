package com.ivannafusion.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ivannafusion.AudioEngine
import com.ivannafusion.PresetManager
import com.ivannafusion.screens.*

@Composable
fun AppNavigation(audioEngine: AudioEngine, presetManager: PresetManager) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(audioEngine, presetManager, navController) }
        composable("pf_engine") { PFEngineScreen(audioEngine, navController) }
        composable("presets") { PresetsScreen(presetManager, audioEngine, navController) }
        composable("settings") { SettingsScreen(audioEngine, navController) }
        composable("ai") { AIScreen(audioEngine, navController) }
        composable("spatial") { SpatialScreen(audioEngine, navController) }
        composable("eq") { DynamicEQScreen(audioEngine, navController) }
        composable("simbiosis") { SimbiosisScreen(audioEngine, navController) }
        composable("monitor") { MonitorScreen(audioEngine, navController) }
    }
}
