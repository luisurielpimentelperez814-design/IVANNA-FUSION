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
fun CompressorScreen() {
    val bands = listOf("Sub (0-200Hz)", "Low (200-1k)", "Mid (1-5k)", "High (5k+)")
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("🎛️ Multiband Compressor", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    Text("Threshold: %.0f dB".format(th), color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(th, { th = it; try { IvannaNativeLib.compSetThreshold(i, it) } catch(e: Exception){} }, -60f..0f)
                    Text("Ratio: %.1f:1".format(ra), color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(ra, { ra = it; try { IvannaNativeLib.compSetRatio(i, it) } catch(e: Exception){} }, 1f..10f)
                    Text("Attack: %.0f ms".format(at), color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(at, { at = it; try { IvannaNativeLib.compSetAttack(i, it) } catch(e: Exception){} }, 1f..100f)
                    Text("Release: %.0f ms".format(re), color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    Slider(re, { re = it; try { IvannaNativeLib.compSetRelease(i, it) } catch(e: Exception){} }, 50f..2000f)
                }
            }
        }
    }
}

@Composable
fun ConvolverScreen() {
    var enabled by remember { mutableStateOf(false) }
    var mix by remember { mutableStateOf(0.3f) }
    var selectedIR by remember { mutableStateOf(0) }
    val irs = listOf("Small Room", "Medium Room", "Large Hall", "Plate Reverb", "Guitar Cabinet", "Headphone Crossfeed")
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("🔊 Convolver (Reverb)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Enabled", color = Color.White, modifier = Modifier.weight(1f))
            Switch(enabled, { enabled = it; try { IvannaNativeLib.convolverSetEnabled(it) } catch(e: Exception){} },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CAF50)))
        }
        Spacer(Modifier.height(16.dp))
        Text("Impulse Response", color = Color.White, fontWeight = FontWeight.Bold)
        irs.forEachIndexed { i, name ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selectedIR == i, onClick = {
                    selectedIR = i                    try { IvannaNativeLib.convolverLoadPreset(i) } catch(e: Exception){}
                }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50)))
                Text(name, color = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Mix: %.0f%%".format(mix * 100), color = Color.White)
        Slider(mix, { mix = it; try { IvannaNativeLib.convolverSetMix(it) } catch(e: Exception){} }, 0f..1f)
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
        Text("🎧 AutoEQ Headphone Correction", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Corrección a target Harman", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text("Selecciona tus auriculares", color = Color.White, fontWeight = FontWeight.Bold)
        headphones.forEachIndexed { i, name ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selected == i, onClick = { selected = i },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50)))
                Text(name, color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            try {
                IvannaNativeLib.autoeqApply(headphones[selected])
                applied = "✓ Applied: ${headphones[selected]}"
            } catch (e: Exception) {
                applied = "✗ Error: ${e.message}"
            }
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) {
            Text("Aplicar Corrección", color = Color.Black)
        }
        if (applied.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(applied, color = if (applied.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFFF5555))        }
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

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            try {
                momentary = IvannaNativeLib.getMomentaryLoudness()
                shortTerm = IvannaNativeLib.getShortTermLoudness()
                integrated = IvannaNativeLib.getIntegratedLoudness()
                peak = IvannaNativeLib.getPeakLevel()
                correlation = IvannaNativeLib.getCorrelation()
                lra = IvannaNativeLib.getLoudnessRange()
            } catch (e: Exception) {}
            kotlinx.coroutines.delay(500)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("📊 Analyzer (BS.1770-4)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Medición profesional de loudness", color = Color(0xFF888888), fontSize = 12.sp)
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column {
                Text(label, color = Color(0xFF888888), fontSize = 11.sp)                Text("%.1f %s".format(value, unit), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PresetsScreen() {
    val presets = listOf(
        "🎵 Flat (Reference)" to "Respuesta plana sin modificaciones",
        "🎛️ Mastering (iZotope)" to "EQ profesional + widener + upscaler",
        "🎬 Dolby Cinema" to "Surround completo + height + bass",
        "🎧 Sony DSEE" to "Restauración de armónicos perdidos",
        "🔊 Bass Head" to "Graves profundos con armónicos",
        "🎤 Vocal Clarity" to "Presencia vocal 1-4kHz",
        "🎮 Gaming" to "Spatial awareness + footsteps",
        "🌙 Late Night" to "Loudness normalization"
    )
    var current by remember { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("💾 Presets Profesionales", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${presets.size} presets integrados", color = Color(0xFF888888), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        presets.forEachIndexed { i, (name, desc) ->
            Card(
                colors = CardDefaults.cardColors(if (current == i) Color(0xFF4CAF50) else Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable {
                        current = i
                        try { IvannaNativeLib.setPreset(i) } catch(e: Exception){}
                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(desc, color = if (current == i) Color.Black else Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var sampleRate by remember { mutableStateOf("44100 Hz") }
    var bufferSize by remember { mutableStateOf("1024 samples") }
    var processingMode by remember { mutableStateOf("Real-time") }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp)) {
        Text("⚙️ Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Audio Engine", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                InfoRow("Sample Rate", sampleRate)
                InfoRow("Buffer Size", bufferSize)
                InfoRow("Processing", processingMode)
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
