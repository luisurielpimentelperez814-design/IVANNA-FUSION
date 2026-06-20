package com.ivannafusion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun CompressorScreen() {
    val bands = listOf("Sub (0-200Hz)", "Low (200-1k)", "Mid (1-5k)", "High (5k+)")
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Multiband Compressor", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("4 bandas (tipo iZotope Ozone)", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        bands.forEachIndexed { i, name ->
            var th by remember { mutableStateOf(-20f) }
            var ra by remember { mutableStateOf(2f) }
            var at by remember { mutableStateOf(10f) }
            var re by remember { mutableStateOf(100f) }
            Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Threshold: " + th.toInt() + " dB", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(value = th, onValueChange = { v -> th = v; try { IvannaNativeLib.compSetThreshold(i, v) } catch(e: Exception){} }, valueRange = -60f..0f)
                    Text("Ratio: " + ra.toInt() + ":1", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(value = ra, onValueChange = { v -> ra = v; try { IvannaNativeLib.compSetRatio(i, v) } catch(e: Exception){} }, valueRange = 1f..10f)
                    Text("Attack: " + at.toInt() + " ms", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(value = at, onValueChange = { v -> at = v; try { IvannaNativeLib.compSetAttack(i, v) } catch(e: Exception){} }, valueRange = 1f..100f)
                    Text("Release: " + re.toInt() + " ms", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(value = re, onValueChange = { v -> re = v; try { IvannaNativeLib.compSetRelease(i, v) } catch(e: Exception){} }, valueRange = 50f..2000f)
                }
            }
        }
    }
}

@Composable
fun ConvolverScreen() {    var enabled by remember { mutableStateOf(false) }
    var mix by remember { mutableStateOf(0.3f) }
    var selectedIR by remember { mutableStateOf(0) }
    val irs = listOf("Small Room", "Medium Room", "Large Hall", "Plate Reverb", "Guitar Cabinet", "Headphone Crossfeed")
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("Convolver (Reverb)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", color = Color.White, modifier = Modifier.weight(1f))
            Switch(enabled, { v -> enabled = v; try { IvannaNativeLib.convolverSetEnabled(v) } catch(e: Exception){} },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CAF50)))
        }
        Spacer(Modifier.height(16.dp))
        Text("Impulse Response", color = Color.White, fontWeight = FontWeight.Bold)
        irs.forEachIndexed { i, name ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedIR == i, onClick = {
                    selectedIR = i
                    try { IvannaNativeLib.convolverLoadPreset(i) } catch(e: Exception){}
                }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50)))
                Text(name, color = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Mix: " + (mix * 100).toInt() + "%", color = Color.White)
        Slider(mix, { v -> mix = v; try { IvannaNativeLib.convolverSetMix(v) } catch(e: Exception){} }, valueRange = 0f..1f)
    }
}

@Composable
fun AutoEQScreen() {
    val headphones = listOf(
        "Sennheiser HD 600", "Sennheiser HD 650", "Sennheiser HD 800 S",
        "Beyerdynamic DT 770 Pro", "Beyerdynamic DT 990 Pro",
        "Audio-Technica ATH-M50x", "Sony WH-1000XM4", "Sony MDR-7506",
        "AKG K702", "Audeze LCD-X", "Focal Clear",
        "Apple AirPods Pro", "Bose QC45"
    )
    var selected by remember { mutableStateOf(0) }
    var applied by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("AutoEQ Headphone Correction", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Correccion a target Harman", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text("Selecciona tus auriculares", color = Color.White, fontWeight = FontWeight.Bold)
        headphones.forEachIndexed { i, name ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == i, onClick = { selected = i },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50)))
                Text(name, color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            try {
                IvannaNativeLib.autoeqApply(headphones[selected])
                applied = "Applied: " + headphones[selected]
            } catch (e: Exception) {
                applied = "Error: " + e.message
            }
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) {
            Text("Aplicar Correccion", color = Color.Black)
        }
        if (applied.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(applied, color = if (applied.startsWith("Applied")) Color(0xFF4CAF50) else Color(0xFFFF5555))
        }
    }
}

@Composable
fun AnalyzerScreen() {
    var momentary by remember { mutableStateOf(-70f) }
    var shortTerm by remember { mutableStateOf(-70f) }
    var integrated by remember { mutableStateOf(-70f) }
    var peak by remember { mutableStateOf(-100f) }
    var correlation by remember { mutableStateOf(0f) }
    var lra by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                momentary = IvannaNativeLib.getMomentaryLoudness()
                shortTerm = IvannaNativeLib.getShortTermLoudness()
                integrated = IvannaNativeLib.getIntegratedLoudness()
                peak = IvannaNativeLib.getPeakLevel()
                correlation = IvannaNativeLib.getCorrelation()
                lra = IvannaNativeLib.getLoudnessRange()
            } catch (e: Exception) {}
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("Analyzer (BS.1770-4)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Medicion profesional de loudness", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        MeterCard("Momentary (400ms)", momentary, "LUFS", Color(0xFF4ECDC4))
        MeterCard("Short-term (3s)", shortTerm, "LUFS", Color(0xFFFFE66D))
        MeterCard("Integrated", integrated, "LUFS", Color(0xFFFF6B6B))
        MeterCard("Peak", peak, "dBFS", Color(0xFFF78FB3))
        MeterCard("Correlation", correlation, "", Color(0xFF95E1D3))
        MeterCard("LRA (Loudness Range)", lra, "LU", Color(0xFFA8E6CF))
    }
}

@Composable
fun MeterCard(label: String, value: Float, unit: String, color: Color) {
    Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label, color = Color(0xFF888888), fontSize = 11.sp)
                Text(value.toInt().toString() + " " + unit, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PresetsScreen() {
    data class PresetItem(val name: String, val desc: String)
    val presets = listOf(
        PresetItem("Flat (Reference)", "Respuesta plana sin modificaciones"),
        PresetItem("Mastering (iZotope)", "EQ profesional + widener + upscaler"),
        PresetItem("Dolby Cinema", "Surround completo + height + bass"),
        PresetItem("Sony DSEE", "Restauracion de armonicos perdidos"),
        PresetItem("Bass Head", "Graves profundos con armonicos"),
        PresetItem("Vocal Clarity", "Presencia vocal 1-4kHz"),
        PresetItem("Gaming", "Spatial awareness + footsteps"),
        PresetItem("Late Night", "Loudness normalization")
    )
    var current by remember { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Presets Profesionales", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(presets.size.toString() + " presets integrados", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        presets.forEachIndexed { i, preset ->
            Card(
                colors = CardDefaults.cardColors(if (current == i) Color(0xFF4CAF50) else Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable {
                        current = i
                        try { IvannaNativeLib.setPreset(i) } catch(e: Exception){}                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(preset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(preset.desc, color = if (current == i) Color.Black else Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Audio Engine", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                InfoRow("Sample Rate", "44100 Hz")
                InfoRow("Buffer Size", "1024 samples")
                InfoRow("Processing", "Real-time")
                InfoRow("Architecture", "ARM64 NEON")
                InfoRow("Build", "v1.0.0-TRASCENDENTAL")
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { try { IvannaNativeLib.reset() } catch(e: Exception){} },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color(0xFFFF5555))) {
            Text("Reset All to Defaults", color = Color.White)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFAAAAAA))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
