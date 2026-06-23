package com.ivannafusion
import com.ivannafusion.dsp.DSPState

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

        // YAMNet (clasificador real, ver YamnetClassifier.kt). Si
        // app/src/main/assets/yamnet.tflite no existe todavía (ver
        // assets/README_MODEL.txt), initialize() devuelve false e
        // isAiClassifierLoaded() queda false — sin crashear y sin
        // simular una clasificación falsa.
        val yamnetLoaded = YamnetClassifier.initialize(context)
        Log.i("AudioEngine", "YAMNet cargado: $yamnetLoaded")

        callback(ok)
    }

    @Volatile private var lastYamnetResult: YamnetClassifier.Result? = null
    private val yamnetAccumulator = FloatArray(YamnetClassifier.INPUT_SAMPLES)
    private var yamnetAccumPos = 0
    private var yamnetSourceSampleRate = 48000
    private var yamnetClassifying = false  // evita solapar inferencias si una tarda más que el bloque siguiente

    fun release() { initialized = false; try { nativeReset() } catch (e: Exception) {}; YamnetClassifier.release() }
    
    /**
     * Punto de entrada para audio MONO ya capturado externamente (ver
     * PlaybackCaptureService — captura de audio interno del sistema vía
     * AudioPlaybackCapture). A diferencia de processAudio(), esto NO
     * pasa por el motor DSP nativo (nativeProcessAudio) — solo alimenta
     * al clasificador YAMNet. Tiene sentido mantenerlos separados:
     * processAudio() es audio que la propia app está reproduciendo/
     * procesando localmente (estéreo), mientras que esto es una copia
     * de solo-análisis de lo que otras apps están reproduciendo (mono,
     * ya a 16kHz porque PlaybackCaptureService captura directo a esa
     * tasa).
     */
    fun feedExternalMonoAudio(monoSamples: FloatArray, sourceSampleRate: Int) {
        if (!YamnetClassifier.isLoaded) return
        yamnetSourceSampleRate = sourceSampleRate
        // feedYamnetAccumulator espera un buffer intercalado L/R; para
        // audio ya mono, L=R=mono duplica la muestra sin alterar el
        // promedio que la función calcula internamente.
        val pseudoStereo = FloatArray(monoSamples.size * 2)
        for (i in monoSamples.indices) {
            pseudoStereo[i * 2] = monoSamples[i]
            pseudoStereo[i * 2 + 1] = monoSamples[i]
        }
        feedYamnetAccumulator(pseudoStereo)
    }

    fun processAudio(input: FloatArray, sr: Int): FloatArray {
        if (!initialized) return input
        val output = FloatArray(input.size)
        try { nativeProcessAudio(input, output, input.size / 2) } catch (e: Exception) { return input }

        if (YamnetClassifier.isLoaded) {
            yamnetSourceSampleRate = sr
            feedYamnetAccumulator(input)
        }

        return output
    }

    /**
     * Convierte el bloque estéreo intercalado a mono (promedio L/R),
     * lo decima a 16kHz (decimación simple por relación de enteros —
     * suficiente para clasificación de eventos de audio, no para
     * reproducción), y lo acumula hasta tener exactamente
     * YamnetClassifier.INPUT_SAMPLES muestras, momento en el que
     * dispara una inferencia en un hilo separado para no bloquear el
     * camino de audio que llamó a processAudio().
     */
    private fun feedYamnetAccumulator(stereoInterleaved: FloatArray) {
        val ratio = (yamnetSourceSampleRate.toFloat() / YamnetClassifier.SAMPLE_RATE_HZ).coerceAtLeast(1f)
        var srcIdx = 0f
        val frames = stereoInterleaved.size / 2
        while (srcIdx.toInt() < frames && yamnetAccumPos < yamnetAccumulator.size) {
            val f = srcIdx.toInt()
            val mono = (stereoInterleaved[f * 2] + stereoInterleaved[f * 2 + 1]) * 0.5f
            yamnetAccumulator[yamnetAccumPos++] = mono
            srcIdx += ratio
        }

        if (yamnetAccumPos >= yamnetAccumulator.size && !yamnetClassifying) {
            yamnetClassifying = true
            val block = yamnetAccumulator.copyOf()
            yamnetAccumPos = 0
            Thread {
                try {
                    val result = YamnetClassifier.classify(block)
                    if (result != null) lastYamnetResult = result
                } finally {
                    yamnetClassifying = false
                }
            }.apply { isDaemon = true; start() }
        }
    }

    fun getInputLevel(): Float = try { nativeGetInputLevel() } catch (e: Exception) { 0f }
    fun getOutputLevel(): Float = try { nativeGetOutputLevel() } catch (e: Exception) { 0f }

    fun eqSetBypass(b: Boolean) { for(i in 0..7) try { nativeSetEQBypass(i, b) } catch (e: Exception) {} }
    fun eqSetGain(band: Int, gain: Float) { try { nativeSetEQGain(band, gain) } catch (e: Exception) {} }
    fun eqSetFreq(band: Int, freq: Float) { try { nativeSetEQFreq(band, freq) } catch (e: Exception) {} }
    fun eqSetQ(band: Int, q: Float) { try { nativeSetEQQ(band, q) } catch (e: Exception) {} }
    fun eqSetEnabled(e: Boolean) { for(i in 0..7) try { nativeSetEQBypass(i, !e) } catch (ex: Exception) {} }

    fun compSetBypass(b: Boolean) { try { nativeSetCompressorBypass(b) } catch (e: Exception) {} }
    fun compSetThreshold(t: Float) { try { nativeSetCompressorThreshold(t) } catch (e: Exception) {} }
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
    fun isAiClassifierLoaded(): Boolean = YamnetClassifier.isLoaded
    fun aiGetDetectedGenre(): String = lastYamnetResult?.topLabel ?: "Unknown"
    fun aiGetConfidence(): Float = lastYamnetResult?.topScore?.coerceIn(0f, 1f) ?: 0.0f
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
    fun setMasterVolumePersisted(v: Float) { DSPState.setMasterVolume(v) }
    fun setBassBoostPersisted(v: Float) { DSPState.setBassBoost(v) }
    fun setMidRangePersisted(v: Float) { DSPState.setMidRange(v) }
    fun setTreblePersisted(v: Float) { DSPState.setTreble(v) }
    fun setReverbLevelPersisted(v: Float) { DSPState.setReverbLevel(v) }
    fun setDelayTimePersisted(v: Float) { DSPState.setDelayTime(v) }
    fun setDelayFeedbackPersisted(v: Float) { DSPState.setDelayFeedback(v) }
    fun setCompressorThresholdPersisted(v: Float) { DSPState.setCompressorThreshold(v) }
    fun setCompressorRatioPersisted(v: Float) { DSPState.setCompressorRatio(v) }
}
