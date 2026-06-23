/*
 * IVANNA-FUSION — DSPState
 * Singleton de estado persistente para todas las pantallas.
 * Respaldado por ParameterStore (DataStore). Los composables leen de aquí
 * y NO necesitan re-cargar al navegar.
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */
package com.ivannafusion

import androidx.compose.runtime.*
import com.ivannafusion.persistence.ParameterStore
import kotlinx.coroutines.*

object DSPState {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var store: ParameterStore? = null

    // ── EQ (10 bandas UI → 8 bandas motor) ──────────────────────────────────
    val eqGains     = mutableStateListOf(0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f)
    var eqBypassed  by mutableStateOf(false)

    // ── Compressor ───────────────────────────────────────────────────────────
    var compThreshold by mutableFloatStateOf(0.5f)
    var compRatio     by mutableFloatStateOf(0.2f)
    var compAttack    by mutableFloatStateOf(0.1f)
    var compRelease   by mutableFloatStateOf(0.3f)
    var compKnee      by mutableFloatStateOf(0.3f)
    var compMakeup    by mutableFloatStateOf(0.0f)
    var compBypassed  by mutableStateOf(false)

    // ── Convolver ────────────────────────────────────────────────────────────
    var convDecay     by mutableFloatStateOf(0.4f)
    var convPreDelay  by mutableFloatStateOf(0.1f)
    var convDamping   by mutableFloatStateOf(0.5f)
    var convDiffusion by mutableFloatStateOf(0.7f)
    var convEarlyMix  by mutableFloatStateOf(0.5f)
    var convMix       by mutableFloatStateOf(0.3f)
    var convType      by mutableStateOf("HALL")

    // ── Spatial ──────────────────────────────────────────────────────────────
    var spatWidth     by mutableFloatStateOf(0.5f)
    var spatDepth     by mutableFloatStateOf(0.5f)
    var spatDiffusion by mutableFloatStateOf(0.3f)
    var spatDelay     by mutableFloatStateOf(0.15f)
    var spatModRate   by mutableFloatStateOf(0.5f)
    var spatMix       by mutableFloatStateOf(1.0f)

    // ── AI ───────────────────────────────────────────────────────────────────
    var aiEnabled     by mutableStateOf(true)
    var aiAutoAdapt   by mutableStateOf(true)
    var aiSensitivity by mutableFloatStateOf(0.7f)
    var aiMode        by mutableStateOf("adaptive")

    // ── PF-ENGINE ────────────────────────────────────────────────────────────
    var pfAmpModel  by mutableIntStateOf(4)
    var pfDrive     by mutableFloatStateOf(1.0f)
    var pfWet       by mutableFloatStateOf(0.6f)
    var pfAlpha     by mutableFloatStateOf(1.0f)
    var pfBeta      by mutableFloatStateOf(0.3f)
    var pfDelta     by mutableFloatStateOf(0.4f)
    var pfSigma     by mutableFloatStateOf(0.5f)
    var pfLowGain   by mutableFloatStateOf(0.0f)
    var pfMidGain   by mutableFloatStateOf(0.0f)
    var pfHighGain  by mutableFloatStateOf(0.0f)
    var pfPresence  by mutableFloatStateOf(0.0f)
    var pfSag       by mutableFloatStateOf(0.1f)

    // ── Global ───────────────────────────────────────────────────────────────
    var masterVolume  by mutableFloatStateOf(1.0f)
    var globalBypass  by mutableStateOf(false)
    var currentPreset by mutableStateOf("default")

    // ── Inicialización ────────────────────────────────────────────────────────
    fun initialize(parameterStore: ParameterStore) {
        store = parameterStore
        scope.launch {
            try {
                parameterStore.getCompressor().collect { comp ->
                    val th = (comp["threshold"] as? Float) ?: -20f
                    val ra = (comp["ratio"] as? Float) ?: 4f
                    val at = (comp["attack"] as? Float) ?: 10f
                    val re = (comp["release"] as? Float) ?: 100f
                    compThreshold = ((-th) / 60f).coerceIn(0f, 1f)
                    compRatio     = ((ra - 1f) / 19f).coerceIn(0f, 1f)
                    compAttack    = (at / 100f).coerceIn(0f, 1f)
                    compRelease   = (re / 1000f).coerceIn(0f, 1f)
                }
            } catch (_: Exception) {}
        }
        scope.launch {
            try {
                parameterStore.getAI().collect { ai ->
                    aiSensitivity = (ai["intensity"] as? Float) ?: 0.7f
                    aiEnabled     = (ai["enabled"] as? Boolean) ?: true
                    aiMode        = (ai["mode"] as? String) ?: "adaptive"
                }
            } catch (_: Exception) {}
        }
        scope.launch {
            try {
                parameterStore.getGlobal().collect { g ->
                    masterVolume  = (g["volume"] as? Float) ?: 1.0f
                    currentPreset = (g["preset"] as? String) ?: "default"
                }
            } catch (_: Exception) {}
        }
        scope.launch {
            try {
                parameterStore.getEQBands().collect { bands ->
                    bands.forEachIndexed { i, band ->
                        if (i < eqGains.size) {
                            eqGains[i] = ((band["gain"] ?: 0f) + 12f) / 24f
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────
    fun saveEQ() = scope.launch {
        val freqs = listOf(31f,62f,125f,250f,500f,1000f,2000f,4000f,8000f,16000f)
        store?.saveEQBands(eqGains.mapIndexed { i, g ->
            mapOf("freq" to freqs[i], "gain" to (g * 24f - 12f), "q" to 1.4f, "enabled" to 1f)
        })
    }

    fun saveCompressor() = scope.launch {
        store?.saveCompressor(
            -(compThreshold * 60f),
            1f + compRatio * 19f,
            compAttack * 100f,
            compRelease * 1000f,
            !compBypassed
        )
    }

    fun saveAI() = scope.launch {
        store?.saveAI(aiSensitivity, aiMode, aiEnabled)
    }

    fun saveGlobal() = scope.launch {
        store?.saveGlobal(masterVolume, globalBypass, currentPreset)
    }
}
