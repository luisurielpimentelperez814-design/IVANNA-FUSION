package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*

class AudioEngine {
    
    companion object {
        init {
            try {
                System.loadLibrary("ivanna_fusion")
                System.loadLibrary("ivanna_fft_effect")
                Log.i("IVANNA_DSP", "✅ Librerías nativas cargadas exitosamente")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("IVANNA_DSP", "❌ Error cargando librerías: ${e.message}")
            }
        }
    }

    private var initialized = false
    private var userMadeAdjustments = false

    // Funciones nativas reales (JNI)
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
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sampleRate = getDeviceSampleRate(context)
                val success = nativeInit(sampleRate, 2) // Stereo
                initialized = success
                Log.i("AudioEngine", "Inicializado con sample rate: $sampleRate, éxito: $success")
                callback(success)
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error en inicialización", e)
                callback(false)
            }
        }
    }

    fun release() {
        initialized = false
        Log.i("AudioEngine", "Released")
    }

    fun processAudio(inputBuffer: FloatArray, sampleRate: Int): FloatArray {
        if (!initialized) return inputBuffer
        val outputBuffer = FloatArray(inputBuffer.size)
        nativeProcessAudio(inputBuffer, outputBuffer, inputBuffer.size / 2)
        return outputBuffer
    }

    // Presets
    fun setPreset(presetName: String) {
        Log.i("AudioEngine", "Preset: $presetName")
    }

    // EQ - Ahora llaman a funciones nativas reales
    fun eqSetBypass(bypass: Boolean) { 
        for (i in 0..7) nativeSetEQBypass(i, bypass)
    }
    fun eqSetGain(band: Int, gain: Float) { nativeSetEQGain(band, gain) }
    fun eqSetFreq(band: Int, freq: Float) { nativeSetEQFreq(band, freq) }
    fun eqSetQ(band: Int, q: Float) { nativeSetEQQ(band, q) }
    fun eqSetEnabled(enabled: Boolean) { 
        for (i in 0..7) nativeSetEQBypass(i, !enabled)
    }

    // Compressor - Funciones nativas reales
    fun compSetBypass(bypass: Boolean) { nativeSetCompressorBypass(bypass) }
    fun compSetThreshold(threshold: Float) { nativeSetCompressorThreshold(threshold) }
    fun compSetRatio(ratio: Float) { nativeSetCompressorRatio(ratio) }
    fun compSetAttack(attack: Float) { nativeSetCompressorAttack(attack) }
    fun compSetRelease(release: Float) { nativeSetCompressorRelease(release) }    fun compSetKnee(knee: Float) { nativeSetCompressorKnee(knee) }
    fun compSetMakeup(makeup: Float) { nativeSetCompressorMakeup(makeup) }
    fun compSetEnabled(enabled: Boolean) { nativeSetCompressorBypass(!enabled) }

    // Convolver
    fun convSetType(type: String) { Log.d("AudioEngine", "convSetType: $type") }
    fun convPresetSmallRoom() { Log.d("AudioEngine", "convPresetSmallRoom") }
    fun convPresetLargeHall() { Log.d("AudioEngine", "convPresetLargeHall") }
    fun convPresetPlate() { Log.d("AudioEngine", "convPresetPlate") }
    fun convPresetSpring() { Log.d("AudioEngine", "convPresetSpring") }
    fun convSetDecay(decay: Float) { Log.d("AudioEngine", "convSetDecay: $decay") }
    fun convSetPreDelay(preDelay: Float) { Log.d("AudioEngine", "convSetPreDelay: $preDelay") }
    fun convSetDamping(damping: Float) { Log.d("AudioEngine", "convSetDamping: $damping") }
    fun convSetDiffusion(diffusion: Float) { Log.d("AudioEngine", "convSetDiffusion: $diffusion") }
    fun convSetEarlyMix(earlyMix: Float) { Log.d("AudioEngine", "convSetEarlyMix: $earlyMix") }
    fun convSetMix(mix: Float) { Log.d("AudioEngine", "convSetMix: $mix") }

    // Decorrelator
    fun decorPresetNatural() { Log.d("AudioEngine", "decorPresetNatural") }
    fun decorPresetWide() { Log.d("AudioEngine", "decorPresetWide") }
    fun decorPresetMonoToStereo() { Log.d("AudioEngine", "decorPresetMonoToStereo") }
    fun decorSetWidth(width: Float) { Log.d("AudioEngine", "decorSetWidth: $width") }
    fun decorSetDepth(depth: Float) { Log.d("AudioEngine", "decorSetDepth: $depth") }
    fun decorSetDiffusion(diffusion: Float) { Log.d("AudioEngine", "decorSetDiffusion: $diffusion") }
    fun decorSetDelay(delay: Float) { Log.d("AudioEngine", "decorSetDelay: $delay") }
    fun decorSetModRate(modRate: Float) { Log.d("AudioEngine", "decorSetModRate: $modRate") }
    fun decorSetMix(mix: Float) { Log.d("AudioEngine", "decorSetMix: $mix") }

    // AI
    fun isAiClassifierLoaded(): Boolean = false
    fun aiGetDetectedGenre(): String = "Unknown"
    fun aiGetConfidence(): Float = 0.0f
    fun aiGetTempo(): Float = 120.0f
    fun aiSetEnabled(enabled: Boolean) { Log.d("AudioEngine", "aiSetEnabled: $enabled") }
    fun aiSetAutoAdapt(autoAdapt: Boolean) { Log.d("AudioEngine", "aiSetAutoAdapt: $autoAdapt") }
    fun aiSetSensitivity(sensitivity: Float) { Log.d("AudioEngine", "aiSetSensitivity: $sensitivity") }
    fun aiGetCurrentCurveName(): String = "Default"
    fun aiGetCurrentCurveDescription(): String = "Default curve"
    fun aiApplyCurrentCurve() { Log.d("AudioEngine", "aiApplyCurrentCurve") }

    // Dashboard metrics
    fun getMomentaryLoudness(): Float = -20.0f
    fun getCorrelation(): Float = 0.8f
    fun getLatencyMicros(): Long = 5000L
    fun getGeneration(): Int = 1
    fun getBestFitness(): Float = 0.95f

    // PF Engine
    fun pfEvoTick(barCount: Int) { Log.d("AudioEngine", "pfEvoTick: $barCount") }
    fun applyPFPreset(preset: Any) { Log.d("AudioEngine", "applyPFPreset: $preset") }    fun pfSetAmp(amp: Int) { Log.d("AudioEngine", "pfSetAmp: $amp") }
    fun pfSetParam(param: String, value: Float) { Log.d("AudioEngine", "pfSetParam: $param=$value") }
    fun pfEvoReset() { Log.d("AudioEngine", "pfEvoReset") }

    // Utility
    fun recordUserAdjustment() {
        userMadeAdjustments = true
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
