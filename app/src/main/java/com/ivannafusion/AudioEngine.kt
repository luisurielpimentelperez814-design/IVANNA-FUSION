package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*

class AudioEngine {
    private val nativeLib = IvannaNativeLib()
    private var userMadeAdjustments = false
    private var initialized = false

    fun initialize(context: Context, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val deviceSampleRate = getDeviceSampleRate(context)
                Log.i("AudioEngine", "Inicializando con sample rate: $deviceSampleRate")
                
                nativeLib.nativeSetEnabled(true)
                nativeLib.nativeReset()
                initialized = true
                
                callback(true)
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error en inicialización", e)
                callback(false)
            }
        }
    }

    fun processAudio(inputBuffer: FloatArray, sampleRate: Int): FloatArray {
        if (!initialized) return inputBuffer
        // Placeholder: el procesamiento real se hará en Fase 2
        return inputBuffer
    }

    fun setPreset(presetName: String) {
        Log.i("AudioEngine", "Aplicando preset: $presetName")
        val presetId = when (presetName.lowercase()) {
            "rock", "classic rock" -> 1
            "bass boost" -> 2
            "vocal clarity" -> 3
            "live room" -> 4
            "cinematic" -> 5
            "studio reference" -> 0
            else -> 0
        }
        nativeLib.nativeSetPreset(presetId)
        userMadeAdjustments = false
    }

    fun recordUserAdjustment() {
        userMadeAdjustments = true
        Log.d("AudioEngine", "Usuario ajustó parámetros manualmente")
    }

    fun getAIModelVersion(): Int = 1
    fun getAITotalExperiences(): Int = 0
    fun getAIInferenceCount(): Long = 0L
    fun aiGetDeviceTemperature(): Float = 35.0f

    private fun getDeviceSampleRate(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateStr?.toIntOrNull() ?: 48000
    }
}
