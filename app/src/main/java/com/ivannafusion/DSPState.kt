package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ivannafusion.persistence.ParameterStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Estado global de la aplicación IVANNA-FUSION.
 * Contiene parámetros DSP, estado de hardware, presets, etc.
 */
object DSPState {
    private const val TAG = "DSPState"

    // ===== PRESETS =====
    var presets: SnapshotStateList<Preset> = mutableStateListOf()

    // ===== PARÁMETROS DSP =====
    var mu: Int = 500
        get() = field
        set(value) { field = value.coerceIn(0, 1000) }

    var spatialEnabled: Boolean = true

    // Posición de escucha (para pruebas)
    var posX: Int = 10
    var posY: Int = 0
    var posZ: Int = 5

    // ===== HARDWARE REAL (detectado del dispositivo) =====
    var deviceSampleRateHz: Int = 48000
        private set
    var deviceFramesPerBuffer: Int = 192
        private set
    var deviceSupportsHighRes: Boolean = false
        private set
    var deviceBufferLatencyUs: Long = 0
        private set

    // ===== INICIALIZACIÓN =====
    suspend fun initialize(store: ParameterStore) {
        Log.d(TAG, "Inicializando DSPState...")
        // Cargar valores guardados (si los hay)
        try {
            val savedMu = store.getInt("mu", 500)
            mu = savedMu
            Log.d(TAG, "mu cargado: $mu")
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando mu", e)
        }
        // Cargar otros parámetros...
    }

    /**
     * Detecta las capacidades reales de hardware del dispositivo.
     * Debe llamarse desde IVANNAApplication.onCreate.
     */
    fun detectRealHardwareCapabilities(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Obtener sample rate nativo (propiedad estándar)
            val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            deviceSampleRateHz = sampleRateStr?.toIntOrNull() ?: 48000

            // Obtener frames por buffer
            val framesStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            deviceFramesPerBuffer = framesStr?.toIntOrNull() ?: 192

            // Detectar si soporta alta resolución (>48kHz y buffer pequeño)
            deviceSupportsHighRes = deviceSampleRateHz >= 96000 && deviceFramesPerBuffer <= 256

            // Calcular latencia estimada
            deviceBufferLatencyUs = if (deviceSampleRateHz > 0 && deviceFramesPerBuffer > 0) {
                (deviceFramesPerBuffer.toLong() * 1_000_000L / deviceSampleRateHz)
            } else 0L

            Log.i(TAG, "✅ Hardware detectado: ${deviceSampleRateHz}Hz, ${deviceFramesPerBuffer} frames, " +
                    "hi-res: $deviceSupportsHighRes, latencia: ${deviceBufferLatencyUs}μs")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error detectando hardware: ${e.message}", e)
            // Valores por defecto seguros
            deviceSampleRateHz = 48000
            deviceFramesPerBuffer = 192
            deviceSupportsHighRes = false
            deviceBufferLatencyUs = 0L
        }
    }
}

/**
 * Clase de datos para un preset DSP.
 */
data class Preset(
    val name: String,
    val params: Map<String, Float> = emptyMap()
)
