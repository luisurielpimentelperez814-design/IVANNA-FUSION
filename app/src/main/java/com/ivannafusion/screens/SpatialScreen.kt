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
fun SpatialSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), color = Color.White, fontSize = 12.sp)
        }
        Slider(value, onChange, valueRange = min..max)
    }
}

@Composable
fun SpatialScreen() {
    var sW by remember { mutableStateOf(1f) }
    var sL by remember { mutableStateOf(0.3f) }
    var sH by remember { mutableStateOf(0.2f) }
    var sR by remember { mutableStateOf(0.4f) }
    var stW by remember { mutableStateOf(1f) }
    var bA by remember { mutableStateOf(0.5f) }
    var bF by remember { mutableStateOf(150f) }
    var uA by remember { mutableStateOf(0.5f) }
    var uC by remember { mutableStateOf(16000f) }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("🌌 Spatial Audio", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(Modifier.height(16.dp))
        Text("SURROUND VIRTUALIZER (Dolby)", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SpatialSlider("Width", sW, 0f, 2f) { v -> sW = v; try { IvannaNativeLib.surroundSetWidth(v) } catch(e: Exception){} }
        SpatialSlider("Surround Level", sL, 0f, 1f) { v -> sL = v; try { IvannaNativeLib.surroundSetLevel(v) } catch(e: Exception){} }
        SpatialSlider("Height", sH, 0f, 1f) { v -> sH = v; try { IvannaNativeLib.surroundSetHeight(v) } catch(e: Exception){} }
        SpatialSlider("Room Size", sR, 0f, 1f) { v -> sR = v; try { IvannaNativeLib.surroundSetRoom(v) } catch(e: Exception){} }

        Spacer(Modifier.height(20.dp))
        Text("STEREO WIDENER", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SpatialSlider("Stereo Width", stW, 0f, 2f) { v -> stW = v; try { IvannaNativeLib.widenerSetWidth(v) } catch(e: Exception){} }

        Spacer(Modifier.height(20.dp))
        Text("BASS ENHANCER (MaxxBass)", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SpatialSlider("Amount", bA, 0f, 1f) { v -> bA = v; try { IvannaNativeLib.bassSetAmount(v) } catch(e: Exception){} }
        SpatialSlider("Frequency (Hz)", bF, 40f, 300f) { v -> bF = v; try { IvannaNativeLib.bassSetFrequency(v) } catch(e: Exception){} }

        Spacer(Modifier.height(20.dp))
        Text("HARMONIC UPSCALER (DSEE)", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SpatialSlider("Amount", uA, 0f, 1f) { v -> uA = v; try { IvannaNativeLib.upscalerSetAmount(v) } catch(e: Exception){} }
        SpatialSlider("Ceiling (Hz)", uC, 8000f, 20000f) { v -> uC = v; try { IvannaNativeLib.upscalerSetCeiling(v) } catch(e: Exception){} }
    }
}
