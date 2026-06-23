package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log

class AudioEngine {
    companion object {
        init {
            try {
                System.loadLibrary("ivanna_jni")
                Log.i("IVANNA_DSP", "✅ Librería nativa cargada")
            } catch (e: Exception) {
                Log.e("IVANNA_DSP", "❌ Error: ${e.message}")
            }
        }
    }

    private var initialized = false

    external fun nativeInit(sampleRate: Int, channels: Int): Boolean
    external fun nativeProcessAudio(input: FloatArray, output: FloatArray, frames: Int)    external fun nativeSetEQGain(band: Int, gain: Float)
    external fun nativeSetEQFreq(band: Int, freq: Float)
    external fun nativeSetEQQ(band: Int, q: Float)
    external fun nativeSetEQBypass(band: Int, bypass: Boolean)
    external fun nativeSetCompressorThreshold(t: Float)
    external fun nativeSetCompressorRatio(r: Float)
    external fun nativeSetCompressorAttack(a: Float)
    external fun nativeSetCompressorRelease(r: Float)
    external fun nativeSetCompressorKnee(k: Float)
    external fun nativeSetCompressorMakeup(m: Float)
    external fun nativeSetCompressorBypass(b: Boolean)
    external fun nativeSetExciterDrive(d: Float)
    external fun nativeSetExciterMix(m: Float)
    external fun nativeSetExciterBypass(b: Boolean)
    external fun nativeSetFFTEffect(enabled: Boolean)
    external fun nativeReset()
    external fun nativeGetInputLevel(): Float
    external fun nativeGetOutputLevel(): Float

    fun initialize(context: Context, callback: (Boolean) -> Unit) {
        val sr = getDeviceSampleRate(context)
        val ok = try {
            nativeInit(sr, 2)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error: ${e.message}")
            false
        }
        initialized = ok
        callback(ok)
    }

    fun release() { initialized = false; try { nativeReset() } catch (e: Exception) {} }
    
    fun processAudio(input: FloatArray, sr: Int): FloatArray {
        if (!initialized) return input
        val output = FloatArray(input.size)
        try { nativeProcessAudio(input, output, input.size / 2) } catch (e: Exception) { return input }
        return output
    }

    fun getInputLevel(): Float = try { nativeGetInputLevel() } catch (e: Exception) { 0f }
    fun getOutputLevel(): Float = try { nativeGetOutputLevel() } catch (e: Exception) { 0f }

    fun eqSetBypass(b: Boolean) { for(i in 0..7) try { nativeSetEQBypass(i, b) } catch (e: Exception) {} }
    fun eqSetGain(band: Int, gain: Float) { try { nativeSetEQGain(band, gain) } catch (e: Exception) {} }
    fun eqSetFreq(band: Int, freq: Float) { try { nativeSetEQFreq(band, freq) } catch (e: Exception) {} }
    fun eqSetQ(band: Int, q: Float) { try { nativeSetEQQ(band, q) } catch (e: Exception) {} }
    fun eqSetEnabled(e: Boolean) { for(i in 0..7) try { nativeSetEQBypass(i, !e) } catch (ex: Exception) {} }

    fun compSetBypass(b: Boolean) { try { nativeSetCompressorBypass(b) } catch (e: Exception) {} }    fun compSetThreshold(t: Float) { try { nativeSetCompressorThreshold(t) } catch (e: Exception) {} }
    fun compSetRatio(r: Float) { try { nativeSetCompressorRatio(r) } catch (e: Exception) {} }
    fun compSetAttack(a: Float) { try { nativeSetCompressorAttack(a) } catch (e: Exception) {} }
    fun compSetRelease(r: Float) { try { nativeSetCompressorRelease(r) } catch (e: Exception) {} }
    fun compSetKnee(k: Float) { try { nativeSetCompressorKnee(k) } catch (e: Exception) {} }
    fun compSetMakeup(m: Float) { try { nativeSetCompressorMakeup(m) } catch (e: Exception) {} }
    fun compSetEnabled(e: Boolean) { try { nativeSetCompressorBypass(!e) } catch (ex: Exception) {} }

    fun excSetDrive(d: Float) { try { nativeSetExciterDrive(d) } catch (e: Exception) {} }
    fun excSetMix(m: Float) { try { nativeSetExciterMix(m) } catch (e: Exception) {} }
    fun excSetBypass(b: Boolean) { try { nativeSetExciterBypass(b) } catch (e: Exception) {} }

    fun fftSetEnabled(e: Boolean) { try { nativeSetFFTEffect(e) } catch (ex: Exception) {} }

    fun convSetType(t: String) {}
    fun convPresetSmallRoom() {}
    fun convPresetLargeHall() {}
    fun convPresetPlate() {}
    fun convPresetSpring() {}
    fun convSetDecay(d: Float) {}
    fun convSetPreDelay(d: Float) {}
    fun convSetDamping(d: Float) {}
    fun convSetDiffusion(d: Float) {}
    fun convSetEarlyMix(m: Float) {}
    fun convSetMix(m: Float) {}
    fun decorPresetNatural() {}
    fun decorPresetWide() {}
    fun decorPresetMonoToStereo() {}
    fun decorSetWidth(w: Float) {}
    fun decorSetDepth(d: Float) {}
    fun decorSetDiffusion(d: Float) {}
    fun decorSetDelay(d: Float) {}
    fun decorSetModRate(r: Float) {}
    fun decorSetMix(m: Float) {}
    fun isAiClassifierLoaded(): Boolean = false
    fun aiGetDetectedGenre(): String = "Unknown"
    fun aiGetConfidence(): Float = 0.0f
    fun aiGetTempo(): Float = 120.0f
    fun aiSetEnabled(e: Boolean) {}
    fun aiSetAutoAdapt(a: Boolean) {}
    fun aiSetSensitivity(s: Float) {}
    fun aiGetCurrentCurveName(): String = "Default"
    fun aiGetCurrentCurveDescription(): String = "Default"
    fun aiApplyCurrentCurve() {}
    fun getMomentaryLoudness(): Float = -20.0f
    fun getCorrelation(): Float = 0.8f
    fun getLatencyMicros(): Long = 5000L
    fun getGeneration(): Int = 1
    fun getBestFitness(): Float = 0.95f
    fun pfEvoTick(b: Int) {}    fun applyPFPreset(p: Any) {}
    fun pfSetAmp(a: Int) {}
    fun pfSetParam(p: String, v: Float) {}
    fun pfEvoReset() {}
    fun recordUserAdjustment() {}
    fun getAIModelVersion(): Int = 1
    fun getAITotalExperiences(): Int = 0
    fun getAIInferenceCount(): Long = 0L
    fun aiGetDeviceTemperature(): Float = 35.0f
    fun setPreset(n: String) {}

    private fun getDeviceSampleRate(ctx: Context): Int {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    }
}
