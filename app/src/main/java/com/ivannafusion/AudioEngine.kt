package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*

class AudioEngine {
    
    companion object {
        init {
            try {
                System.loadLibrary("ivanna_jni")
                Log.i("IVANNA_DSP", "✅ Motor DSP JNI cargado")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("IVANNA_DSP", "❌ Error: ${e.message}")
            }
        }
    }

    private var initialized = false

    external fun nativeInit(sampleRate: Int, channels: Int): Boolean
    external fun nativeProcessAudio(inputBuffer: FloatArray, outputBuffer: FloatArray, numFrames: Int)
    external fun nativeSetEQGain(band: Int, gainDB: Float)
    external fun nativeSetEQFreq(band: Int, freqHz: Float)
    external fun nativeSetEQQ(band: Int, q: Float)
    external fun nativeSetEQBypass(band: Int, bypass: Boolean)
    external fun nativeSetCompressorThreshold(thresholdDB: Float)
    external fun nativeSetCompressorRatio(ratio: Float)
    external fun nativeSetCompressorAttack(attackMs: Float)
    external fun nativeSetCompressorRelease(releaseMs: Float)
    external fun nativeSetCompressorKnee(kneeDB: Float)
    external fun nativeSetCompressorMakeup(makeupDB: Float)
    external fun nativeSetCompressorBypass(bypass: Boolean)
    external fun nativeSetExciterDrive(drive: Float)
    external fun nativeSetExciterMix(mix: Float)
    external fun nativeSetExciterBypass(bypass: Boolean)
    external fun nativeSetFFTEffect(enabled: Boolean)
    external fun nativeReset()

    fun initialize(context: Context, callback: (Boolean) -> Unit) {
        val sampleRate = getDeviceSampleRate(context)
        val success = try {
            nativeInit(sampleRate, 2)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error en nativeInit: ${e.message}")            false
        }
        initialized = success
        Log.i("AudioEngine", "Inicializado: $sampleRate Hz, éxito: $success")
        callback(success)
    }

    fun release() { 
        initialized = false
        try { nativeReset() } catch (e: Exception) {}
    }

    fun processAudio(inputBuffer: FloatArray, sampleRate: Int): FloatArray {
        if (!initialized) return inputBuffer
        val outputBuffer = FloatArray(inputBuffer.size)
        try {
            nativeProcessAudio(inputBuffer, outputBuffer, inputBuffer.size / 2)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error en processAudio: ${e.message}")
            return inputBuffer
        }
        return outputBuffer
    }

    // EQ
    fun eqSetBypass(bypass: Boolean) { 
        for(i in 0..7) {
            try { nativeSetEQBypass(i, bypass) } catch (e: Exception) {}
        }
    }
    fun eqSetGain(band: Int, gain: Float) { 
        try { nativeSetEQGain(band, gain) } catch (e: Exception) {}
    }
    fun eqSetFreq(band: Int, freq: Float) { 
        try { nativeSetEQFreq(band, freq) } catch (e: Exception) {}
    }
    fun eqSetQ(band: Int, q: Float) { 
        try { nativeSetEQQ(band, q) } catch (e: Exception) {}
    }
    fun eqSetEnabled(enabled: Boolean) { 
        for(i in 0..7) {
            try { nativeSetEQBypass(i, !enabled) } catch (e: Exception) {}
        }
    }

    // Compressor
    fun compSetBypass(bypass: Boolean) { 
        try { nativeSetCompressorBypass(bypass) } catch (e: Exception) {}
    }
    fun compSetThreshold(threshold: Float) {         try { nativeSetCompressorThreshold(threshold) } catch (e: Exception) {}
    }
    fun compSetRatio(ratio: Float) { 
        try { nativeSetCompressorRatio(ratio) } catch (e: Exception) {}
    }
    fun compSetAttack(attack: Float) { 
        try { nativeSetCompressorAttack(attack) } catch (e: Exception) {}
    }
    fun compSetRelease(release: Float) { 
        try { nativeSetCompressorRelease(release) } catch (e: Exception) {}
    }
    fun compSetKnee(knee: Float) { 
        try { nativeSetCompressorKnee(knee) } catch (e: Exception) {}
    }
    fun compSetMakeup(makeup: Float) { 
        try { nativeSetCompressorMakeup(makeup) } catch (e: Exception) {}
    }
    fun compSetEnabled(enabled: Boolean) { 
        try { nativeSetCompressorBypass(!enabled) } catch (e: Exception) {}
    }

    // Placeholders para UI
    fun convSetType(type: String) {}
    fun convPresetSmallRoom() {}
    fun convPresetLargeHall() {}
    fun convPresetPlate() {}
    fun convPresetSpring() {}
    fun convSetDecay(decay: Float) {}
    fun convSetPreDelay(preDelay: Float) {}
    fun convSetDamping(damping: Float) {}
    fun convSetDiffusion(diffusion: Float) {}
    fun convSetEarlyMix(earlyMix: Float) {}
    fun convSetMix(mix: Float) {}
    fun decorPresetNatural() {}
    fun decorPresetWide() {}
    fun decorPresetMonoToStereo() {}
    fun decorSetWidth(width: Float) {}
    fun decorSetDepth(depth: Float) {}
    fun decorSetDiffusion(diffusion: Float) {}
    fun decorSetDelay(delay: Float) {}
    fun decorSetModRate(modRate: Float) {}
    fun decorSetMix(mix: Float) {}
    fun isAiClassifierLoaded(): Boolean = false
    fun aiGetDetectedGenre(): String = "Unknown"
    fun aiGetConfidence(): Float = 0.0f
    fun aiGetTempo(): Float = 120.0f
    fun aiSetEnabled(enabled: Boolean) {}
    fun aiSetAutoAdapt(autoAdapt: Boolean) {}
    fun aiSetSensitivity(sensitivity: Float) {}
    fun aiGetCurrentCurveName(): String = "Default"    fun aiGetCurrentCurveDescription(): String = "Default curve"
    fun aiApplyCurrentCurve() {}
    fun getMomentaryLoudness(): Float = -20.0f
    fun getCorrelation(): Float = 0.8f
    fun getLatencyMicros(): Long = 5000L
    fun getGeneration(): Int = 1
    fun getBestFitness(): Float = 0.95f
    fun pfEvoTick(barCount: Int) {}
    fun applyPFPreset(preset: Any) {}
    fun pfSetAmp(amp: Int) {}
    fun pfSetParam(param: String, value: Float) {}
    fun pfEvoReset() {}
    fun recordUserAdjustment() {}
    fun getAIModelVersion(): Int = 1
    fun getAITotalExperiences(): Int = 0
    fun getAIInferenceCount(): Long = 0L
    fun aiGetDeviceTemperature(): Float = 35.0f
    fun setPreset(presetName: String) {}

    private fun getDeviceSampleRate(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateStr?.toIntOrNull() ?: 48000
    }
}
