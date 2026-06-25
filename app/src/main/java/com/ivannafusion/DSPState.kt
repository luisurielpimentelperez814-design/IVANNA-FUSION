package com.ivannafusion

import android.media.AudioManager
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object DSPState {
    // ===== PRESETS =====
    var presets: SnapshotStateList<Preset> = mutableStateListOf()
    
    // ===== MOTOR ESPACIAL =====
    var mu: Int = 500
        get() = field
        set(value) { field = value.coerceIn(0, 1000) }
    
    var spatialEnabled: Boolean = true
    var posX: Int = 10
    var posY: Int = 0
    var posZ: Int = 5

    // ===== HARDWARE REAL (cargado desde la aplicación) =====
    var deviceSampleRateHz: Int = 48000
        private set
    var deviceFramesPerBuffer: Int = 192
        private set
    var deviceSupportsHighRes: Boolean = false
        private set

    /**
     * Inicializa los valores de hardware reales del dispositivo.
     * Llamar desde IVANNAApplication.onCreate() o desde la primera actividad.
     */
    fun detectHardwareCapabilities(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val framesPerBufferStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

        deviceSampleRateHz = sampleRateStr?.toIntOrNull() ?: 48000
        deviceFramesPerBuffer = framesPerBufferStr?.toIntOrNull() ?: 192
        deviceSupportsHighRes = deviceSampleRateHz >= 96000
    }
}
