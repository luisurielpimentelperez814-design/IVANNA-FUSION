package com.ivannafusion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivannafusion.IvannaNativeLib
import kotlinx.coroutines.delay

@Composable
fun AIAssistantScreen() {
    var aiEnabled by remember { mutableStateOf(false) }
    var autoAdapt by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableStateOf(0.7f) }
    var genre by remember { mutableStateOf("ANALYZING") }
    var confidence by remember { mutableStateOf(0f) }
    var tempo by remember { mutableStateOf(0f) }
    var curveName by remember { mutableStateOf("Waiting...") }
    var curveDescription by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                genre = IvannaNativeLib.aiGetDetectedGenre()
                confidence = IvannaNativeLib.aiGetConfidence()
                tempo = IvannaNativeLib.aiGetTempo()
                curveName = IvannaNativeLib.aiGetCurrentCurveName()
                curveDescription = IvannaNativeLib.aiGetCurrentCurveDescription()
            } catch (e: Exception) {}
            delay(500)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("🧠 AI Assistant", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Análisis autónomo en tiempo real", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI Activo", color = Color.White, modifier = Modifier.weight(1f))
            Switch(aiEnabled, { aiEnabled = it; try { IvannaNativeLib.aiSetEnabled(it) } catch(e: Exception){} },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CAF50)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-Adapt", color = Color.White, modifier = Modifier.weight(1f))
            Switch(autoAdapt, { autoAdapt = it; try { IvannaNativeLib.aiSetAutoAdapt(it) } catch(e: Exception){} },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CAF50)))
        }
        Text("Sensibilidad: %.2f".format(sensitivity), color = Color(0xFFAAAAAA))
        Slider(
            value = sensitivity,
            onValueChange = { sensitivity = it; try { IvannaNativeLib.aiSetSensitivity(it) } catch(e: Exception){} },
            valueRange = 0f..1f
        )

        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("DETECTION", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val gc = when(genre.lowercase()) {
                    "rock" -> Color(0xFFFF6B6B); "electronic" -> Color(0xFF4ECDC4)
                    "hiphop" -> Color(0xFFFFE66D); "jazz" -> Color(0xFF95E1D3)
                    "classical" -> Color(0xFFA8E6CF); "metal" -> Color(0xFFC44569)
                    "pop" -> Color(0xFFF78FB3); "ambient" -> Color(0xFF9B59B6)
                    else -> Color.White
                }
                Text(genre, color = gc, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Confidence: %.0f%%".format(confidence*100), color = Color(0xFFAAAAAA))
                Text("Tempo: %.0f BPM".format(tempo), color = Color(0xFFAAAAAA))
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("AI-GENERATED CURVE", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(curveName, color = Color(0xFF00FF00), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (curveDescription.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(curveDescription, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { try { IvannaNativeLib.aiApplyCurrentCurve() } catch(e: Exception){} },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color(0xFF4ECDC4))) {
            Text("Aplicar AI Curve", color = Color.Black)        }
    }
}
