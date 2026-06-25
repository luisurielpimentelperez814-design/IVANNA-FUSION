package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object DSPState {
    private const val TAG = "DSPState"

    var presets: SnapshotStateList<Preset> = mutableStateListOf()

    // Parámetros DSP
    var mu: Int = 500
        get() = field
        set(value) { field = value.coerceIn(0, 1000) }

    var spatialEnabled: Boolean = true
    var posX: Int = 10
    var posY: Int = 0
    var posZ: Int = 5

    // PF-Engine
    var pfAmpModel: Int = 0
    var pfDrive: Float = 0.5f
    var pfWet: Float = 0.3f
    var pfAlpha: Float = 1.0f
    var pfDelta: Float = 0.5f
    var pfSigma: Float = 0.5f
    var pfLowGain: Float = 0.0f
    var pfMidGain: Float = 0.0f
    var pfHighGain: Float = 0.0f
    var pfPresence: Float = 0.0f

    // AI
    var aiEnabled: Boolean = false
    var aiAutoAdapt: Boolean = false
    var aiSensitivity: Float = 0.5f

    // Hardware
    var deviceSampleRateHz: Int = 48000
        private set
    var deviceFramesPerBuffer: Int = 192
        private set
    var deviceSupportsHighRes: Boolean = false
        private set
    var deviceBufferLatencyUs: Long = 0
        private set

    suspend fun initialize(store: ParameterStore) {
        Log.d(TAG, "Inicializando DSPState...")
        // Aquí podrías cargar valores guardados si implementas persistencia
    }

    fun detectRealHardwareCapabilities(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            deviceSampleRateHz = sampleRateStr?.toIntOrNull() ?: 48000
            val framesStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            deviceFramesPerBuffer = framesStr?.toIntOrNull() ?: 192
            deviceSupportsHighRes = deviceSampleRateHz >= 96000 && deviceFramesPerBuffer <= 256
            deviceBufferLatencyUs = if (deviceSampleRateHz > 0 && deviceFramesPerBuffer > 0) {
                (deviceFramesPerBuffer.toLong() * 1_000_000L / deviceSampleRateHz)
            } else 0L
            Log.i(TAG, "✅ Hardware detectado: ${deviceSampleRateHz}Hz, ${deviceFramesPerBuffer} frames")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error detectando hardware", e)
        }
    }
}

data class Preset(val name: String, val params: Map<String, Float> = emptyMap())
