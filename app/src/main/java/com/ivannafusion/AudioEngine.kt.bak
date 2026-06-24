package com.ivannafusion

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * AudioEngine - Motor de audio DSP con Homeostasis Universal
 * Capa unificada para Universal y 
 */
class AudioEngine {
    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        
        /**
         * Motor de Homeostasis Universal
         * p* = (n + μ*Ω) / (1 + μ)
         */
        fun homeostasis(n: Float, omega: Float, mu: Float = 0.3f): Float {
            if (omega.isNaN() || omega.isInfinite()) return n
            if (n.isNaN() || n.isInfinite()) return omega
            return (n + mu * omega) / (1.0f + mu)
        }
    }

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Estado del DSP
    private val eqGains = FloatArray(8) { 0f }
    private var compThreshold = -20f
    private var compRatio = 4f
    private var exciterDrive = 1f
    
    // Estado homeostático
    @Volatile private var homeostaticRmsDb = -60f
    @Volatile private var homeostaticSpectrum = FloatArray(32)
    @Volatile private var homeostaticCorrelation = 1f
    @Volatile private var homeostaticLatencyMicros = 5000
    @Volatile private var homeostaticGeneration = 0
    @Volatile private var homeostaticFitness = 0f
    @Volatile private var homeostaticTempo = 120f
    @Volatile private var homeostaticGenre = "ROCK"
    
    private val spectrumHistory = ArrayDeque<FloatArray>()
    private val onsetHistory = ArrayDeque<Long>()
    
    init {
        try {
            System.loadLibrary("ivanna_jni")
            Log.i(TAG, "Librería nativa cargada")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Error cargando librería nativa", e)
        }
    }

    fun initialize(context: Context, callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                if (bufferSize <= 0) {                    callback(false)
                    return@launch
                }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    callback(false)
                    return@launch
                }

                try {
                    nativeInit(SAMPLE_RATE, 2)
                } catch (e: Exception) {
                    Log.w(TAG, "Native init falló: ${e.message}")
                }

                audioRecord?.startRecording()
                startProcessing()
                callback(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error en inicialización", e)
                callback(false)
            }
        }
    }

    private fun startProcessing() {
        processingJob = scope.launch {
            val buffer = FloatArray(2048)
            val outputBuffer = FloatArray(2048)
            var frameCounter = 0
            
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    try {
                        nativeProcessAudio(buffer, outputBuffer, read / 2)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error en procesamiento: ${e.message}")
                    }
                    
                    frameCounter++
                    if (frameCounter % 10 == 0) {                        updateMetrics(buffer, read)
                    }
                }
                delay(10)
            }
        }
    }

    private suspend fun updateMetrics(buffer: FloatArray, size: Int) {
        withContext(Dispatchers.Default) {
            try {
                var sum = 0f
                for (i in 0 until size) sum += buffer[i] * buffer[i]
                val rms = sqrt(sum / size)
                val rawRms = if (rms > 0.0001f) 20 * log10(rms).toFloat() else -60f
                homeostaticRmsDb = homeostasis(homeostaticRmsDb, rawRms, 0.3f)
                
                val rawSpectrum = calculateFFT(buffer, size)
                homeostaticSpectrum = FloatArray(32) { i ->
                    val adaptiveMu = 0.3f * (1.0f + abs(rawSpectrum[i] - homeostaticSpectrum[i]))
                    homeostasis(homeostaticSpectrum[i], rawSpectrum[i], adaptiveMu)
                }
                
                val rawCorrelation = calculateCorrelation(buffer, size)
                homeostaticCorrelation = homeostasis(homeostaticCorrelation, rawCorrelation, 0.2f)
                
                val rawBpm = detectBPM(buffer, size)
                if (rawBpm > 0) {
                    homeostaticTempo = homeostasis(homeostaticTempo, rawBpm, 0.1f)
                }
                
                val rawGenre = detectGenre(homeostaticSpectrum)
                if (rawGenre == homeostaticGenre || Math.random() > 0.7) {
                    homeostaticGenre = rawGenre
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando métricas: ${e.message}")
            }
        }
    }

    private fun calculateFFT(buffer: FloatArray, size: Int): FloatArray {
        val spectrum = FloatArray(32)
        val fftSize = minOf(512, size)
        
        for (band in 0 until 32) {
            val freq = 20f * (1000f / 20f).pow(band / 31f)
            val k = (freq * fftSize / SAMPLE_RATE).toInt().coerceIn(1, fftSize / 2 - 1)
            
            var real = 0f            var imag = 0f
            for (n in 0 until fftSize) {
                val angle = 2 * PI * k * n / fftSize
                real += buffer[n] * cos(angle).toFloat()
                imag -= buffer[n] * sin(angle).toFloat()
            }
            
            val magnitude = sqrt(real * real + imag * imag) / fftSize
            spectrum[band] = magnitude.coerceIn(0f, 1f)
        }
        
        spectrumHistory.addLast(spectrum.copyOf())
        if (spectrumHistory.size > 5) spectrumHistory.removeFirst()
        
        val smoothed = FloatArray(32)
        for (i in 0 until 32) {
            var sum = 0f
            spectrumHistory.forEach { sum += it[i] }
            smoothed[i] = sum / spectrumHistory.size
        }
        
        return smoothed
    }

    private fun calculateCorrelation(buffer: FloatArray, size: Int): Float {
        var sumL = 0f
        var sumR = 0f
        var sumLR = 0f
        
        for (i in 0 until size - 1 step 2) {
            val L = buffer[i]
            val R = buffer[i + 1]
            sumL += L * L
            sumR += R * R
            sumLR += L * R
        }
        
        val denom = sqrt(sumL * sumR)
        return if (denom > 0.0001f) (sumLR / denom).coerceIn(-1f, 1f) else 1f
    }

    private fun detectBPM(buffer: FloatArray, size: Int): Float {
        val now = System.currentTimeMillis()
        val energy = buffer.take(size).sumOf { (it * it).toDouble() }.toFloat() / size
        
        if (energy > 0.01f) {
            onsetHistory.addLast(now)
            if (onsetHistory.size > 20) onsetHistory.removeFirst()
        }
                if (onsetHistory.size >= 4) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until onsetHistory.size) {
                intervals.add(onsetHistory[i] - onsetHistory[i - 1])
            }
            
            val avgInterval = intervals.average()
            if (avgInterval > 0) {
                val bpm = (60000.0 / avgInterval).toFloat()
                if (bpm in 60f..180f) return bpm
            }
        }
        return -1f
    }

    private fun detectGenre(spectrum: FloatArray): String {
        val lowEnergy = spectrum.take(8).average()
        val midEnergy = spectrum.slice(8..20).average()
        val highEnergy = spectrum.takeLast(11).average()
        
        return when {
            lowEnergy > 0.6 && midEnergy < 0.4 -> "BASS"
            highEnergy > 0.5 && lowEnergy < 0.3 -> "ELECTRONIC"
            midEnergy > 0.5 && lowEnergy > 0.3 -> "ROCK"
            highEnergy > 0.4 && midEnergy > 0.4 -> "POP"
            lowEnergy > 0.4 && highEnergy > 0.3 -> "JAZZ"
            else -> "UNKNOWN"
        }
    }

    // Getters
    fun aiGetRmsDb() = homeostaticRmsDb
    fun aiGetSpectrum() = homeostaticSpectrum
    fun getCorrelation() = homeostaticCorrelation
    fun getLatencyMicros() = homeostaticLatencyMicros
    fun getGeneration() = homeostaticGeneration
    fun getBestFitness() = homeostaticFitness
    fun aiGetDetectedGenre() = homeostaticGenre
    fun aiGetTempo() = homeostaticTempo

    // Setters
    fun setEQGain(band: Int, gain: Float) {
        if (band in 0..7) {
            eqGains[band] = gain
            try { nativeSetEQGain(band, gain) } catch (e: Exception) {}
        }
    }

    fun setCompressorThreshold(threshold: Float) {
        compThreshold = threshold        try { nativeSetCompressorThreshold(threshold) } catch (e: Exception) {}
    }

    fun setCompressorRatio(ratio: Float) {
        compRatio = ratio
        try { nativeSetCompressorRatio(ratio) } catch (e: Exception) {}
    }

    fun setExciterDrive(drive: Float) {
        exciterDrive = drive
        try { nativeSetExciterDrive(drive) } catch (e: Exception) {}
    }

    fun release() {
        processingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        scope.cancel()
    }

    // Native methods
    private external fun nativeInit(sampleRate: Int, channels: Int): Boolean
    private external fun nativeProcessAudio(input: FloatArray, output: FloatArray, frames: Int)
    private external fun nativeSetEQGain(band: Int, gain: Float)
    private external fun nativeSetCompressorThreshold(threshold: Float)
    private external fun nativeSetCompressorRatio(ratio: Float)
    private external fun nativeSetExciterDrive(drive: Float)
}

    // ================= SAFE JNI STUBS =================
    private fun nativeSetCompressorBypass(v: Float) {}
    private fun nativeSetCompressorKnee(v: Float) {}
    private fun nativeSetCompressorMakeup(v: Float) {}
    private fun nativeApplyPFPreset(v: Any) {}
    private fun nativePfSetAmp(v: Int) {}


    // ================= SAFE JNI STUBS =================
    private fun nativeSetCompressorBypass(v: Float) {}
    private fun nativeSetCompressorKnee(v: Float) {}
    private fun nativeSetCompressorMakeup(v: Float) {}
    private fun nativeApplyPFPreset(v: Any) {}
    private fun nativePfSetAmp(v: Int) {}

