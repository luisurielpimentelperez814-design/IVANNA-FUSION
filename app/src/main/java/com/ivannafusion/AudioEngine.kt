package com.ivannafusion

import android.content.Context
import android.util.Log
import kotlin.math.abs
import kotlin.random.Random

class AudioEngine {
    companion object {
        private const val TAG = "AudioEngine"
        var audio_fs_hz: Int = 48000
            private set
        var audio_bit_depth: Int = 16
            private set
        var audio_latencia_us: Int = 5000
            private set
    }

    var initialized: Boolean = false
        private set

    private var mutationRate: Float = 0.01f
    private var generation: Int = 0
    private var bestFitness: Float = 0.0f
    private var phaseErrorRms: Float = 0.0f
    private var fusionLevel: Float = 0.5f
    private var latencyMicros: Long = 5000L
    private var isEnabled: Boolean = false
    private var detectedGenre: String = "Unknown"
    private var confidence: Float = 0.5f
    private var tempo: Float = 120.0f
    private var currentCurveName: String = "Flat"
    private var currentCurveDesc: String = "Sin procesar"

    fun initialize(context: Context): Boolean {
        return try {
            Log.d(TAG, "Inicializando AudioEngine...")
            initialized = MagiskBridge.isModuleInstalled()
            isEnabled = initialized
            Log.d(TAG, "Módulo Magisk instalado: $initialized")
            initialized
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando", e)
            false
        }
    }

    fun startAudioCapture() {
        try {            Log.d(TAG, "Iniciando captura")
            isEnabled = true
            MagiskBridge.sendCommand("start")
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    fun stopAudioCapture() {
        try {
            Log.d(TAG, "Deteniendo captura")
            isEnabled = false
            MagiskBridge.sendCommand("stop")
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    fun release() {
        try {
            initialized = false
            isEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    fun restart() {
        release()
        MagiskBridge.sendCommand("restart")
    }

    fun getEvolutionState(): String = "Gen: $generation | Fit: ${String.format("%.3f", bestFitness)}"
    fun getLatencyMicros(): Long = latencyMicros
    fun getPhaseErrorRms(): Float = phaseErrorRms
    fun getGeneration(): Int = generation
    fun getBestFitness(): Float = bestFitness
    fun getMutationRate(): Float = mutationRate

    fun setMutationRate(rate: Float) {
        mutationRate = rate.coerceIn(0f, 1f)
        MagiskBridge.setParameter("mutation", mutationRate)
    }

    fun setFusionLevel(level: Float) {
        fusionLevel = level.coerceIn(0f, 1f)
        MagiskBridge.setWet(fusionLevel)
    }

    fun setEnabled(enabled: Boolean) {        isEnabled = enabled
        if (enabled) startAudioCapture() else stopAudioCapture()
    }

    fun initializeEvolution() {
        generation = 0
        bestFitness = 0.0f
        MagiskBridge.sendCommand("evo:init")
        Log.d(TAG, "Evolución inicializada")
    }

    fun evolveStep() {
        generation++
        bestFitness = (bestFitness + Random.nextFloat() * 0.05f).coerceAtMost(1.0f)
        phaseErrorRms = Random.nextFloat() * 0.1f
        latencyMicros = 3000L + Random.nextLong(4000L)
        MagiskBridge.sendCommand("evo:step")
    }

    fun setPreferredAudioConfig(sampleRate: Int, bitDepth: Int) {
        audio_fs_hz = sampleRate
        audio_bit_depth = bitDepth
        MagiskBridge.sendCommand("config:sample_rate=$sampleRate;bit_depth=$bitDepth")
    }

    fun predictSamples(): FloatArray = FloatArray(128) { Random.nextFloat() * 2f - 1f }

    fun aiGetDetectedGenre(): String = detectedGenre
    fun aiGetConfidence(): Float = confidence
    fun aiGetTempo(): Float = tempo
    fun aiGetCurrentCurveName(): String = currentCurveName
    fun aiGetCurrentCurveDescription(): String = currentCurveDesc

    fun aiSetEnabled(enabled: Boolean) {
        isEnabled = enabled
        MagiskBridge.sendCommand("ai:${if (enabled) "enable" else "disable"}")
    }

    fun aiSetAutoAdapt(auto: Boolean) {
        Log.d(TAG, "Auto adapt: $auto")
        MagiskBridge.sendCommand("ai:auto:${if (auto) "on" else "off"}")
    }

    fun aiSetSensitivity(sens: Float) {
        confidence = sens
        MagiskBridge.setParameter("ai_sensitivity", sens)
    }

    fun aiApplyCurrentCurve() {
        currentCurveName = "Applied"        MagiskBridge.sendCommand("ai:apply")
    }

    fun eqSetGain(band: Int, gain: Float) {
        Log.d(TAG, "EQ Band $band: $gain dB")
        MagiskBridge.setEQBand(band, gain)
    }

    fun eqSetThreshold(thr: Float) {
        Log.d(TAG, "EQ Threshold: $thr")
        MagiskBridge.setParameter("eq_threshold", thr)
    }

    fun eqSetRatio(ratio: Float) {
        Log.d(TAG, "EQ Ratio: $ratio")
        MagiskBridge.setParameter("eq_ratio", ratio)
    }

    fun compSetThreshold(thr: Float) {
        Log.d(TAG, "Comp Threshold: $thr")
        MagiskBridge.setParameter("comp_threshold", thr)
    }

    fun compSetRatio(ratio: Float) {
        Log.d(TAG, "Comp Ratio: $ratio")
        MagiskBridge.setParameter("comp_ratio", ratio)
    }

    fun compSetAttack(attack: Float) {
        Log.d(TAG, "Comp Attack: $attack")
        MagiskBridge.setParameter("comp_attack", attack)
    }

    fun compSetRelease(release: Float) {
        Log.d(TAG, "Comp Release: $release")
        MagiskBridge.setParameter("comp_release", release)
    }

    fun convolverSetEnabled(enabled: Boolean) {
        Log.d(TAG, "Convolver: $enabled")
        MagiskBridge.sendCommand("convolver:${if (enabled) "enable" else "disable"}")
    }

    fun convolverLoadPreset(name: String) {
        Log.d(TAG, "Convolver preset: $name")
        MagiskBridge.loadPreset(name)
    }

    fun convolverSetMix(mix: Float) {
        Log.d(TAG, "Convolver mix: $mix")        MagiskBridge.setWet(mix)
    }

    fun surroundSetWidth(w: Float) {
        Log.d(TAG, "Surround Width: $w")
        MagiskBridge.setParameter("surround_width", w)
    }

    fun surroundSetLevel(l: Float) {
        Log.d(TAG, "Surround Level: $l")
        MagiskBridge.setParameter("surround_level", l)
    }

    fun surroundSetHeight(h: Float) {
        Log.d(TAG, "Surround Height: $h")
        MagiskBridge.setParameter("surround_height", h)
    }

    fun surroundSetRoom(r: Float) {
        Log.d(TAG, "Surround Room: $r")
        MagiskBridge.setParameter("surround_room", r)
    }

    fun widenerSetWidth(w: Float) {
        Log.d(TAG, "Widener: $w")
        MagiskBridge.setParameter("widener_width", w)
    }

    fun bassSetAmount(a: Float) {
        Log.d(TAG, "Bass Amount: $a")
        MagiskBridge.setParameter("bass_amount", a)
    }

    fun bassSetFrequency(f: Float) {
        Log.d(TAG, "Bass Freq: $f")
        MagiskBridge.setParameter("bass_freq", f)
    }

    fun upscalerSetAmount(a: Float) {
        Log.d(TAG, "Upscaler Amount: $a")
        MagiskBridge.setParameter("upscaler_amount", a)
    }

    fun upscalerSetCeiling(c: Float) {
        Log.d(TAG, "Upscaler Ceiling: $c")
        MagiskBridge.setParameter("upscaler_ceiling", c)
    }

    fun getMomentaryLoudness(): Float = -20f + Random.nextFloat() * 10f
    fun getShortTermLoudness(): Float = -25f + Random.nextFloat() * 10f    fun getIntegratedLoudness(): Float = -23f + Random.nextFloat() * 5f
    fun getPeakLevel(): Float = -1f - Random.nextFloat() * 5f
    fun getCorrelation(): Float = 0.8f + Random.nextFloat() * 0.2f
    fun getLoudnessRange(): Float = 10f + Random.nextFloat() * 5f

    fun setPreset(name: String) {
        Log.d(TAG, "Preset: $name")
        MagiskBridge.loadPreset(name)
    }

    fun autoeqApply() {
        Log.d(TAG, "AutoEQ applied")
        MagiskBridge.sendCommand("autoeq:apply")
    }

    fun reset() {
        generation = 0
        bestFitness = 0f
        fusionLevel = 0.5f
        MagiskBridge.sendCommand("reset")
    }
}
