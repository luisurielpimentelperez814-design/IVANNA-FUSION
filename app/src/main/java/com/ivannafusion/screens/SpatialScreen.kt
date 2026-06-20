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
        SS("Width", sW, 0f, 2f) { sW = it; try { IvannaNativeLib.surroundSetWidth(it) } catch(e: Exception){} }        SS("Surround Level", sL, 0f, 1f) { sL = it; try { IvannaNativeLib.surroundSetLevel(it) } catch(e: Exception){} }
        SS("Height", sH, 0f, 1f) { sH = it; try { IvannaNativeLib.surroundSetHeight(it) } catch(e: Exception){} }
        SS("Room Size", sR, 0f, 1f) { sR = it; try { IvannaNativeLib.surroundSetRoom(it) } catch(e: Exception){} }

        Spacer(Modifier.height(20.dp))
        Text("STEREO WIDENER", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SS("Stereo Width", stW, 0f, 2f) { stW = it; try { IvannaNativeLib.widenerSetWidth(it) } catch(e: Exception){} }

        Spacer(Modifier.height(20.dp))
        Text("BASS ENHANCER (MaxxBass)", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SS("Amount", bA, 0f, 1f) { bA = it; try { IvannaNativeLib.bassSetAmount(it) } catch(e: Exception){} }
        SS("Frequency (Hz)", bF, 40f, 300f) { bF = it; try { IvannaNativeLib.bassSetFrequency(it) } catch(e: Exception){} }

        Spacer(Modifier.height(20.dp))
        Text("HARMONIC UPSCALER (DSEE)", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        SS("Amount", uA, 0f, 1f) { uA = it; try { IvannaNativeLib.upscalerSetAmount(it) } catch(e: Exception){} }
        SS("Ceiling (Hz)", uC, 8000f, 20000f) { uC = it; try { IvannaNativeLib.upscalerSetCeiling(it) } catch(e: Exception){} }
    }
}

@Composable
fun SS(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), color = Color.White, fontSize = 12.sp)
        }
        Slider(value, onChange, valueRange = min..max)
    }
}
