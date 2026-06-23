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
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sampleRate = getDeviceSampleRate(context)
                val success = nativeInit(sampleRate, 2)
                initialized = success                callback(success)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    fun release() { initialized = false; nativeReset() }

    fun processAudio(inputBuffer: FloatArray, sampleRate: Int): FloatArray {
        if (!initialized) return inputBuffer
        val outputBuffer = FloatArray(inputBuffer.size)
        nativeProcessAudio(inputBuffer, outputBuffer, inputBuffer.size / 2)
        return outputBuffer
    }

    // EQ
    fun eqSetBypass(bypass: Boolean) { for(i in 0..7) nativeSetEQBypass(i, bypass) }
    fun eqSetGain(band: Int, gain: Float) { nativeSetEQGain(band, gain) }
    fun eqSetFreq(band: Int, freq: Float) { nativeSetEQFreq(band, freq) }
    fun eqSetQ(band: Int, q: Float) { nativeSetEQQ(band, q) }
    fun eqSetEnabled(enabled: Boolean) { for(i in 0..7) nativeSetEQBypass(i, !enabled) }

    // Compressor
    fun compSetBypass(bypass: Boolean) { nativeSetCompressorBypass(bypass) }
    fun compSetThreshold(threshold: Float) { nativeSetCompressorThreshold(threshold) }
    fun compSetRatio(ratio: Float) { nativeSetCompressorRatio(ratio) }
    fun compSetAttack(attack: Float) { nativeSetCompressorAttack(attack) }
    fun compSetRelease(release: Float) { nativeSetCompressorRelease(release) }
    fun compSetKnee(knee: Float) { nativeSetCompressorKnee(knee) }
    fun compSetMakeup(makeup: Float) { nativeSetCompressorMakeup(makeup) }
    fun compSetEnabled(enabled: Boolean) { nativeSetCompressorBypass(!enabled) }

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
    fun decorSetDepth(depth: Float) {}    fun decorSetDiffusion(diffusion: Float) {}
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
    fun aiGetCurrentCurveName(): String = "Default"
    fun aiGetCurrentCurveDescription(): String = "Default curve"
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
