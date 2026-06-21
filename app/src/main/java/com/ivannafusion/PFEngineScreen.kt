/*
 * IVANNA-FUSION TRASCENDENTAL — PF ENGINE PRO MAX NEXT
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * Pantalla de control del PF ENGINE v3.0.0
 * Comunica con pf-daemon vía pf_ctl (socket /data/pf/pf.sock)
 * Parámetros: alpha·beta·gamma·delta·sigma | drive | wet | EQ | amp model
 */

package com.ivannafusion

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "IVANNA-PFEngine"

private val AMP_MODELS = listOf("Marshall", "Fender", "Vox", "70s Rock", "Bypass")

private val BG_TOP    = Color(0xFF0A0A0F)
private val BG_BOT    = Color(0xFF0D0D1A)
private val ACCENT    = Color(0xFF7B61FF)
private val ACCENT2   = Color(0xFF00E5FF)
private val CARD_BG   = Color(0xFF12121E)
private val TEXT_DIM  = Color(0xFF8888BB)

// ── Ejecuta pf_ctl en shell root ────────────────────────────────────────────
private fun pfCtl(cmd: String): String {
    return try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/pf_ctl $cmd"))
        val out  = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        Log.d(TAG, "pf_ctl $cmd → $out")
        out.trim()
    } catch (e: Exception) {
        Log.w(TAG, "pf_ctl error: ${e.message}")
        "error"
    }
}

@Composable
fun PFEngineScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    // Parámetros espectrales
    var alpha   by remember { mutableFloatStateOf(1.0f) }   // tilt espectral
    var beta    by remember { mutableFloatStateOf(0.3f) }   // densidad armónica
    var gamma   by remember { mutableFloatStateOf(0.5f) }   // shaping transitorio
    var delta   by remember { mutableFloatStateOf(0.4f) }   // profundidad distorsión
    var sigma   by remember { mutableFloatStateOf(0.5f) }   // amplitud espacial
    var drive   by remember { mutableFloatStateOf(1.0f) }   // ganancia amp
    var wet     by remember { mutableFloatStateOf(0.6f) }   // wet/dry
    var lowGain by remember { mutableFloatStateOf(0.0f) }   // EQ bajo dB
    var midGain by remember { mutableFloatStateOf(0.0f) }   // EQ medio dB
    var hiGain  by remember { mutableFloatStateOf(0.0f) }   // EQ alto dB
    var ampIdx  by remember { mutableIntStateOf(4) }        // 0=Marshall…4=Bypass
    var daemonStatus by remember { mutableStateOf("–") }
    var busy    by remember { mutableStateOf(false) }

    // Envía todos los parámetros de golpe
    fun sendParams() {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            val cmd = "\"alpha=${"%.2f".format(alpha)};beta=${"%.2f".format(beta)};" +
                      "gamma=${"%.2f".format(gamma)};delta=${"%.2f".format(delta)};" +
                      "sigma=${"%.2f".format(sigma)};drive=${"%.2f".format(drive)};" +
                      "wet=${"%.2f".format(wet)};low=${"%.1f".format(lowGain)};" +
                      "mid=${"%.1f".format(midGain)};high=${"%.1f".format(hiGain)}\""
            pfCtl(cmd)
            pfCtl("amp:$ampIdx")
            withContext(Dispatchers.Main) { busy = false }
        }
    }

    // Poll de estado del daemon
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val s = pfCtl("status")
                withContext(Dispatchers.Main) {
                    daemonStatus = if (s.isBlank() || s == "error") "daemon offline" else s
                }
            }
            delay(3000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BG_TOP, BG_BOT)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Text(
                "PF ENGINE v3.0",
                color = ACCENT,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "AMP · STEMS · PRESETS · EVOLUTION",
                color = TEXT_DIM,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "daemon: $daemonStatus",
                color = if (daemonStatus.contains("offline")) Color(0xFFFF4444) else ACCENT2,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            HorizontalDivider(color = ACCENT.copy(alpha = 0.3f))

            // ── Amp Model ────────────────────────────────────────────────────
            PFCard(title = "AMP MODEL") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AMP_MODELS.forEachIndexed { i, name ->
                        FilterChip(
                            selected = ampIdx == i,
                            onClick  = {
                                ampIdx = i
                                scope.launch(Dispatchers.IO) { pfCtl("amp:$i") }
                            },
                            label = { Text(name, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ACCENT,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            }

            // ── Drive & Wet ──────────────────────────────────────────────────
            PFCard(title = "DRIVE / WET") {
                PFSlider("Drive", drive, 0f, 4f) { drive = it }
                PFSlider("Wet",   wet,   0f, 1f) { wet   = it }
            }

            // ── Parámetros espectrales ────────────────────────────────────────
            PFCard(title = "SPECTRAL PARAMS") {
                PFSlider("α  tilt",      alpha, 0f, 2f) { alpha = it }
                PFSlider("β  harmonic",  beta,  0f, 1f) { beta  = it }
                PFSlider("γ  transient", gamma, 0f, 1f) { gamma = it }
                PFSlider("δ  distort",   delta, 0f, 1f) { delta = it }
                PFSlider("σ  width",     sigma, 0f, 1f) { sigma = it }
            }

            // ── EQ de 3 bandas ───────────────────────────────────────────────
            PFCard(title = "3-BAND EQ  (dB)") {
                PFSlider("Low",  lowGain, -12f, 12f) { lowGain = it }
                PFSlider("Mid",  midGain, -12f, 12f) { midGain = it }
                PFSlider("High", hiGain,  -12f, 12f) { hiGain  = it }
            }

            // ── Botones de acción ────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick  = { sendParams() },
                    enabled  = !busy,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = ACCENT)
                ) {
                    Text(if (busy) "Enviando…" else "APLICAR", fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick  = { navController.navigate("presets") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("PRESETS", fontFamily = FontFamily.Monospace, color = ACCENT2)
                }
            }

            // ── Nav inferior ─────────────────────────────────────────────────
            HorizontalDivider(color = ACCENT.copy(alpha = 0.2f))
            OutlinedButton(
                onClick  = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("← ATRÁS", fontFamily = FontFamily.Monospace, color = TEXT_DIM)
            }
        }
    }
}

@Composable
private fun PFCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = CARD_BG)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ACCENT2, fontSize = 11.sp,
                 fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            content()
        }
    }
}

@Composable
private fun PFSlider(label: String, value: Float, min: Float, max: Float, onChanged: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TEXT_DIM, fontSize = 11.sp,
             fontFamily = FontFamily.Monospace, modifier = Modifier.width(100.dp))
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT)
        )
        Text("${"%.2f".format(value)}", color = ACCENT2, fontSize = 11.sp,
             fontFamily = FontFamily.Monospace, modifier = Modifier.width(46.dp))
    }
}
