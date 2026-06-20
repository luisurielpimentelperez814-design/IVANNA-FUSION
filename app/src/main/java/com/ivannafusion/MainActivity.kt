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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ivannafusion.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
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
                                selected = false,
                                onClick = { navController.navigate(route) },                                colors = NavigationBarItemDefaults.colors(
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
                }
            }
        }
    }
}
