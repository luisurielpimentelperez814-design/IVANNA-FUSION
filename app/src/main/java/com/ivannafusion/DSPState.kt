package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ivannafusion.persistence.ParameterStore

object DSPState {
    private const val TAG = "DSPState"

    var presets: SnapshotStateList<Preset> = mutableStateListOf()

    // ── Posición / Spatial ────────────────────────────────────────────────────
    var spatialEnabled: Boolean = true
    var mu: Int = 500
        set(value) { field = value.coerceIn(0, 1000) }
    var posX: Int = 10
    var posY: Int = 0
    var posZ: Int = 5

    // ── EQ 10 bandas (normalizado 0..1, 0.5 = 0 dB) ──────────────────────────
    val eqGains = mutableStateListOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
                                      0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    var eqBypassed: Boolean = false

    fun saveEQ() { /* DataStore pendiente — los valores ya viven en eqGains */ }

    // ── Compresor (normalizado 0..1) ──────────────────────────────────────────
    var compThreshold by mutableFloatStateOf(0.5f)   // 0 = 0 dB  / 1 = -60 dB
    var compRatio     by mutableFloatStateOf(0.2f)   // 0 = 1:1   / 1 = 20:1
    var compAttack    by mutableFloatStateOf(0.1f)   // 0 = 0 ms  / 1 = 200 ms
    var compRelease   by mutableFloatStateOf(0.3f)   // 0 = 0 ms  / 1 = 3000 ms
    var compKnee      by mutableFloatStateOf(0.125f) // 0 = 0 dB  / 1 = 24 dB
    var compMakeup    by mutableFloatStateOf(0.0f)   // 0 = 0 dB  / 1 = 36 dB
    var compBypassed: Boolean = false

    fun saveCompressor() { /* DataStore pendiente */ }

    // ── Convolver ─────────────────────────────────────────────────────────────
    var convType      by mutableStateOf("HALL")
    var convDecay     by mutableFloatStateOf(0.4f)   // 0..1 → 0.1..10 s
    var convPreDelay  by mutableFloatStateOf(0.1f)   // 0..1 → 0..500 ms
    var convDamping   by mutableFloatStateOf(0.5f)
    var convDiffusion by mutableFloatStateOf(0.7f)
    var convEarlyMix  by mutableFloatStateOf(0.5f)
    var convMix       by mutableFloatStateOf(0.3f)   // 0..1 → 0..100 %
    var convEarlyDelay by mutableFloatStateOf(0.2f)
    var convEarlyDecay by mutableFloatStateOf(0.1f)

    // ── Spatial / Decorrelador ────────────────────────────────────────────────
    var spatWidth     by mutableFloatStateOf(0.5f)   // 0..1 → 0..5
    var spatDepth     by mutableFloatStateOf(0.5f)
    var spatDiffusion by mutableFloatStateOf(0.5f)
    var spatDelay     by mutableFloatStateOf(0.2f)   // 0..1 → 0..200 ms
    var spatModRate   by mutableFloatStateOf(0.5f)   // 0..1 → 0..15 Hz
    var spatMix       by mutableFloatStateOf(0.3f)   // 0..1 → 0..100 %

    // ── PF-Engine ─────────────────────────────────────────────────────────────
    var pfAmpModel: Int = 0
    var pfDrive    by mutableFloatStateOf(0.5f)
    var pfWet      by mutableFloatStateOf(0.3f)
    var pfAlpha    by mutableFloatStateOf(1.0f)
    var pfBeta     by mutableFloatStateOf(0.0f)
    var pfDelta    by mutableFloatStateOf(0.5f)
    var pfSigma    by mutableFloatStateOf(0.5f)
    var pfLowGain  by mutableFloatStateOf(0.0f)
    var pfMidGain  by mutableFloatStateOf(0.0f)
    var pfHighGain by mutableFloatStateOf(0.0f)
    var pfPresence by mutableFloatStateOf(0.0f)
    var pfFreq     by mutableFloatStateOf(1000f)
    var pfResonance by mutableFloatStateOf(0.0f)
    var pfMix      by mutableFloatStateOf(0.5f)

    // ── AI / Motor adaptativo ─────────────────────────────────────────────────
    var aiEnabled     by mutableStateOf(false)
    var aiAutoAdapt   by mutableStateOf(true)
    var aiSensitivity by mutableFloatStateOf(0.5f)

    // ── Hardware (read-only desde fuera) ──────────────────────────────────────
    var deviceSampleRateHz:     Int     = 48000;  private set
    var deviceFramesPerBuffer:  Int     = 192;    private set
    var deviceSupportsHighRes:  Boolean = false;  private set
    var deviceBufferLatencyUs:  Long    = 0;      private set

    // ── Init ──────────────────────────────────────────────────────────────────
    suspend fun initialize(store: ParameterStore) {
        Log.d(TAG, "DSPState inicializado")
    }

    fun detectRealHardwareCapabilities(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            deviceSampleRateHz    = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
            deviceFramesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
            deviceSupportsHighRes = deviceSampleRateHz >= 96000 && deviceFramesPerBuffer <= 256
            deviceBufferLatencyUs = if (deviceSampleRateHz > 0)
                deviceFramesPerBuffer.toLong() * 1_000_000L / deviceSampleRateHz else 0L
            Log.i(TAG, "Hardware: ${deviceSampleRateHz} Hz, ${deviceFramesPerBuffer} frames/buf")
        } catch (e: Exception) {
            Log.e(TAG, "Error detectando hardware", e)
        }
    }
}

data class Preset(val name: String, val params: Map<String, Float> = emptyMap())
