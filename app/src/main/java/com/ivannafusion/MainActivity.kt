package com.ivannafusion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ivannafusion.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route
            Scaffold(
                containerColor = Color(0xFF121212),
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ) {
                        val items = listOf(
                            "home" to "🏠", "ai" to "🧠",
                            "eq" to "🎚️", "spatial" to "🌌", "more" to "⋮"
                        )
                        items.forEach { (route, icon) ->
                            NavigationBarItem(
                                icon = { Text(icon, fontSize = 20.sp) },
                                label = { Text(route.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF4CAF50),
                                    unselectedIconColor = Color(0xFF888888)
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.padding(padding)
                ) {
                    composable("home") { HomeScreen(navController) }
                    composable("ai") { AIAssistantScreen() }
                    composable("eq") { DynamicEQScreen() }
                    composable("spatial") { SpatialScreen() }
                    composable("more") { MoreMenuScreen(navController) }
                    composable("compressor") { CompressorScreen() }
                    composable("convolver") { ConvolverScreen() }
                    composable("autoeq") { AutoEQScreen() }
                    composable("analyzer") { AnalyzerScreen() }
                    composable("presets") { PresetsScreen() }
                    composable("settings") { SettingsScreen() }

                    // Experiencia avanzada IVANNA FUSION TRASCENDENTAL: estaba
                    // completamente implementada (gestos, Kalman/SHM, FusionDial,
                    // PF Engine, auditoría) pero nunca registrada en el NavHost.
                    composable("intro") { IntroScreen(navController) }
                    composable("simbiosis") { SimbiosisScreen(navController) }
                    composable("monitor") { MonitorScreen(navController, AudioEngine, ShmManager) }
                    composable("pf_engine") { PFEngineScreen(navController) }
                    composable("settings_audit") { SettingsAuditScreen(navController) }
                    // "effects" lo referencia SimbiosisScreen pero no existía
                    // ninguna pantalla con ese destino; reutiliza el hub ya
                    // existente de efectos en vez de crear una pantalla nueva.
                    composable("effects") { MoreMenuScreen(navController) }
                }
            }
        }
    }
}
