package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ivannafusion.persistence.ParameterStore

/**
 * Estado global de parámetros DSP y persistencia ligera.
 * Mantiene una única definición de cada parámetro para evitar ambigüedades
 * entre pantallas Compose y el motor nativo.
 */
object DSPState {
    private const val TAG = "DSPState"

    val presets: SnapshotStateList<Preset> = mutableStateListOf()

    // ── Spatial ───────────────────────────────────────────────────────────────
    var spatialEnabled: Boolean = true
    var mu: Int = 500
        set(value) { field = value.coerceIn(0, 1000) }
    var posX: Int = 10
    var posY: Int = 0
    var posZ: Int = 5

    // ── EQ 10 bandas (0..1 donde 0.5 ≈ 0 dB) ────────────────────────────────
    var eqGains: FloatArray = FloatArray(10) { 0.5f }
    var eqBypassed: Boolean = false
    fun saveEQ() { /* hook de persistencia */ }

    // ── Compresor ─────────────────────────────────────────────────────────────
    var compThreshold: Float = 0.667f
    var compRatio: Float = 0.158f
    var compAttack: Float = 0.1f
    var compRelease: Float = 0.3f
    var compKnee: Float = 0.3f
    var compMakeup: Float = 0.0f
    var compBypassed: Boolean = false
    fun saveCompressor() { /* hook de persistencia */ }

    // ── Convolver ─────────────────────────────────────────────────────────────
    var convType: String = "HALL"
    var convDecay: Float = 0.4f
    var convPreDelay: Float = 0.1f
    var convDamping: Float = 0.5f
    var convDiffusion: Float = 0.7f
    var convEarlyMix: Float = 0.5f
    var convMix: Float = 0.3f
    var convEarlyDelay: Float = 0.2f
    var convEarlyDecay: Float = 0.1f

    // ── Spatial / decorrelación ──────────────────────────────────────────────
    var spatWidth: Float = 0.5f
    var spatDepth: Float = 0.5f
    var spatDiffusion: Float = 0.5f
    var spatDelay: Float = 0.2f
    var spatModRate: Float = 0.5f
    var spatMix: Float = 0.3f

    // ── PF-Engine ────────────────────────────────────────────────────────────
    var pfAmpModel: Int = 0
    var pfDrive: Float = 0.5f
    var pfWet: Float = 0.3f
    var pfAlpha: Float = 1.0f
    var pfBeta: Float = 0.0f
    var pfDelta: Float = 0.5f
    var pfSigma: Float = 0.5f
    var pfLowGain: Float = 0.0f
    var pfMidGain: Float = 0.0f
    var pfHighGain: Float = 0.0f
    var pfPresence: Float = 0.0f
    var pfFreq: Float = 1000f
    var pfResonance: Float = 0.0f
    var pfMix: Float = 0.5f

    // ── AI ───────────────────────────────────────────────────────────────────
    var aiEnabled: Boolean = false
    var aiAutoAdapt: Boolean = true
    var aiSensitivity: Float = 0.5f

    // ── Hardware ──────────────────────────────────────────────────────────────
    var deviceSampleRateHz: Int = 48000
        private set
    var deviceFramesPerBuffer: Int = 192
        private set
    var deviceSupportsHighRes: Boolean = false
        private set
    var deviceBufferLatencyUs: Long = 0L
        private set

    suspend fun initialize(store: ParameterStore) {
        Log.d(TAG, "DSPState inicializado")
    }

    fun detectRealHardwareCapabilities(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            deviceSampleRateHz = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull() ?: 48000
            deviceFramesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull() ?: 192
            deviceSupportsHighRes = deviceSampleRateHz >= 96000 && deviceFramesPerBuffer <= 256
            deviceBufferLatencyUs = if (deviceSampleRateHz > 0) {
                deviceFramesPerBuffer.toLong() * 1_000_000L / deviceSampleRateHz
            } else {
                0L
            }
            Log.i(TAG, "Hardware: ${deviceSampleRateHz} Hz / ${deviceFramesPerBuffer} frames")
        } catch (e: Exception) {
            Log.e(TAG, "Error detectando hardware", e)
        }
    }
}

data class Preset(val name: String, val params: Map<String, Float> = emptyMap())
