package com.ivannafusion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivannafusion.IvannaNativeLib

@Composable
fun DynamicEQScreen() {
    val freqs = listOf("60Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz")
    val gains = remember { mutableStateListOf(*FloatArray(8).toTypedArray()) }
    val thresholds = remember { mutableStateListOf(*FloatArray(8) { -20f }.toTypedArray()) }
    val ratios = remember { mutableStateListOf(*FloatArray(8) { 1f }.toTypedArray()) }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("🎚️ Dynamic EQ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("8 bandas + compresión dinámica", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        freqs.forEachIndexed { i, f ->
            Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(f, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                        Text("%.1f dB".format(gains[i]), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                    Text("Gain", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                    Slider(
                        value = gains[i],
                        onValueChange = { gains[i] = it; try { IvannaNativeLib.eqSetGain(i, it) } catch(e: Exception){} },
                        valueRange = -12f..12f
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f).padding(end = 4.dp)) {
                            Text("Threshold", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                            Slider(
                                value = thresholds[i],
                                onValueChange = { thresholds[i] = it; try { IvannaNativeLib.eqSetThreshold(i, it) } catch(e: Exception){} },
                                valueRange = -60f..0f
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text("Ratio", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                            Slider(
                                value = ratios[i],
                                onValueChange = { ratios[i] = it; try { IvannaNativeLib.eqSetRatio(i, it) } catch(e: Exception){} },
                                valueRange = 1f..10f
                            )
                        }
                    }
                }
            }
        }
    }
}
