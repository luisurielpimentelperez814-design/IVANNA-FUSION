package com.ivannafusion.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ivannafusion.screens.*

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController = navController) }
        composable(Routes.PF_ENGINE) { PFEngineScreen(navController = navController) }
        composable(Routes.PRESETS) { PresetsScreen(navController = navController) }
        composable(Routes.SPATIAL) { SpatialScreen(navController = navController) }
        composable(Routes.EQ) { EQScreen(navController = navController) }
        composable(Routes.MONITOR) { MonitorScreen(navController = navController) }
        composable(Routes.AI) { AIScreen(navController = navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController = navController) }
        composable(Routes.SIMBIOSIS) { SimbiosisScreen(navController = navController) }
    }
}
