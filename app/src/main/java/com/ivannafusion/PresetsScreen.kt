/*
 * IVANNA-FUSION TRASCENDENTAL — GESTOR DE PRESETS
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * Listado y carga de presets PF ENGINE (.pfp) desde /data/pf/presets/
 * Presets incluidos: 70s_rock | clean_studio | marshall_crunch | psychedelic | vox_sparkle
 */

package com.ivannafusion

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private const val PTAG = "IVANNA-Presets"

// Presets incluidos en el módulo (flasheados a /data/pf/presets/ por customize.sh)
private data class PFPreset(
    val id:          String,
    val displayName: String,
    val description: String,
    val genre:       String
)

private val BUILT_IN_PRESETS = listOf(
    PFPreset("70s_rock",        "70s Rock",        "Grand Funk Railroad / Rush vibe. Crunch mid-heavy.", "Rock clásico"),
    PFPreset("clean_studio",    "Clean Studio",    "Transparente, lineal. Ideal para grabación limpia.",  "Referencia"),
    PFPreset("marshall_crunch", "Marshall Crunch",  "Amp Marshall en saturación media. Presencia alta.",  "Hard Rock"),
    PFPreset("psychedelic",     "Psychedelic",      "Chorus + saturación suave + presencia etérea.",       "Psicodélico"),
    PFPreset("vox_sparkle",    "Vox Sparkle",      "Amplificador Vox AC30. Brillante, abierto.",          "Britpop / Blues")
)

private val BG_TOP   = Color(0xFF0A0A0F)
private val BG_BOT   = Color(0xFF0D0D1A)
private val ACCENT   = Color(0xFF7B61FF)
private val ACCENT2  = Color(0xFF00E5FF)
private val CARD_BG  = Color(0xFF12121E)
private val TEXT_DIM = Color(0xFF8888BB)
private val SUCCESS  = Color(0xFF00FF9C)

private fun pfLoad(presetId: String): Boolean {
    return try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/pf_ctl load:$presetId"))
        proc.waitFor() == 0
    } catch (e: Exception) {
        Log.w(PTAG, "pfLoad error: ${e.message}")
        false
    }
}

private fun pfSave(presetId: String): Boolean {
    return try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/pf_ctl save:$presetId"))
        proc.waitFor() == 0
    } catch (e: Exception) {
        Log.w(PTAG, "pfSave error: ${e.message}")
        false
    }
}

@Composable
fun PresetsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var activePreset by remember { mutableStateOf<String?>(null) }
    var feedback     by remember { mutableStateOf("") }
    var saveDialog   by remember { mutableStateOf(false) }
    var saveName     by remember { mutableStateOf("") }
    var loading      by remember { mutableStateOf(false) }

    // Auto-limpiar feedback
    LaunchedEffect(feedback) {
        if (feedback.isNotBlank()) {
            delay(2500)
            feedback = ""
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Text(
                "PRESETS PF ENGINE",
                color = ACCENT,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "5 presets integrados · auto-learning activo",
                color = TEXT_DIM,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            if (feedback.isNotBlank()) {
                Text(
                    feedback,
                    color   = if (feedback.startsWith("✓")) SUCCESS else Color(0xFFFF4444),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = ACCENT.copy(alpha = 0.3f))

            // ── Lista de presets ─────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BUILT_IN_PRESETS) { preset ->
                    val isActive = activePreset == preset.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isActive) 1.dp else 0.dp,
                                color = if (isActive) ACCENT else Color.Transparent,
                                shape = CardDefaults.shape
                            )
                            .clickable(enabled = !loading) {
                                loading = true
                                scope.launch(Dispatchers.IO) {
                                    val ok = pfLoad(preset.id)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            activePreset = preset.id
                                            feedback = "✓ ${preset.displayName} cargado"
                                        } else {
                                            feedback = "✗ Error al cargar ${preset.displayName}"
                                        }
                                        loading = false
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) CARD_BG else CARD_BG.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.displayName,
                                    color = if (isActive) ACCENT else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    preset.description,
                                    color = TEXT_DIM,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "[ ${preset.genre} ]",
                                    color = ACCENT2,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (isActive) {
                                Text(
                                    "▶",
                                    color = ACCENT,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = ACCENT.copy(alpha = 0.2f))

            // ── Guardar preset personalizado ─────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick  = { saveDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GUARDAR ACTUAL", fontFamily = FontFamily.Monospace, color = ACCENT2, fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick  = { navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("← ATRÁS", fontFamily = FontFamily.Monospace, color = TEXT_DIM, fontSize = 11.sp)
                }
            }
        }

        // ── Spinner mientras carga ───────────────────────────────────────────
        if (loading) {
            CircularProgressIndicator(
                color    = ACCENT,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    // ── Diálogo: guardar preset ──────────────────────────────────────────────
    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false; saveName = "" },
            title = { Text("Guardar preset", fontFamily = FontFamily.Monospace) },
            text  = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it.replace(" ", "_").lowercase() },
                    label = { Text("nombre (sin espacios)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saveName.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            val ok = pfSave(saveName)
                            withContext(Dispatchers.Main) {
                                feedback = if (ok) "✓ Guardado: $saveName" else "✗ Error al guardar"
                                saveDialog = false
                                saveName   = ""
                            }
                        }
                    }
                }) { Text("GUARDAR", color = ACCENT) }
            },
            dismissButton = {
                TextButton(onClick = { saveDialog = false; saveName = "" }) {
                    Text("CANCELAR", color = TEXT_DIM)
                }
            },
            containerColor = CARD_BG
        )
    }
}
