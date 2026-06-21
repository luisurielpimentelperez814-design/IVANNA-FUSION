package com.ivannafusion

import android.content.Context
import android.util.Log

/**
 * Puente JNI para el motor Ω_in Edge AI
 * Conecta la UI con el pipeline C++/ExecuTorch
 */
object OmegaEngineBridge {
    private const val TAG = "OmegaEngineBridge"
    
    // Cargar librería nativa
    init {
        try {
            System.loadLibrary("omega_engine_native")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DECLARACIONES JNI (implementadas en audio_pipeline_android.cpp)
    // ═══════════════════════════════════════════════════════════════════════
    
    @JvmStatic
    external fun nativeInitialize(modelPath: String): Boolean
    
    @JvmStatic
    external fun nativeStartAudio(): Boolean
    
    @JvmStatic
    external fun nativeStopAudio()
    
    @JvmStatic
    external fun nativeIsInitialized(): Boolean
    
    @JvmStatic
    external fun nativeIsProcessing(): Boolean
    
    @JvmStatic
    external fun nativeGetLatencyMs(): Float
    
    // ═══════════════════════════════════════════════════════════════════════
    // API DE ALTO NIVEL (para la UI)
    // ═══════════════════════════════════════════════════════════════════════
    
    private var initialized = false    private var processing = false
    
    fun initialize(context: Context): Boolean {
        if (initialized) {
            Log.w(TAG, "Already initialized")
            return true
        }
        
        // Ruta del modelo .pte en assets o almacenamiento
        val modelPath = "${context.filesDir}/omega_engine_int8.pte"
        
        // TODO: Copiar modelo desde assets si no existe
        // copyModelFromAssets(context, modelPath)
        
        initialized = nativeInitialize(modelPath)
        Log.i(TAG, "Initialization result: $initialized")
        return initialized
    }
    
    fun start(): Boolean {
        if (!initialized) {
            Log.e(TAG, "Cannot start: not initialized")
            return false
        }
        
        processing = nativeStartAudio()
        Log.i(TAG, "Start result: $processing")
        return processing
    }
    
    fun stop() {
        nativeStopAudio()
        processing = false
        Log.i(TAG, "Stopped")
    }
    
    fun isInitialized(): Boolean = initialized && nativeIsInitialized()
    
    fun isProcessing(): Boolean = processing && nativeIsProcessing()
    
    fun getLatencyMs(): Float = if (processing) nativeGetLatencyMs() else 0f
    
    // ═══════════════════════════════════════════════════════════════════════
    // PARÁMETROS DEL MOTOR Ω_in (para controles de UI)
    // ═══════════════════════════════════════════════════════════════════════
    
    data class OmegaParams(
        var swdProjections: Int = 64,        // L en SWD (16-128)
        var phaseCoherence: Float = 0.8f,    // Intensidad de phase locking (0-1)
        var collapseStrength: Float = 0.5f,  // Fuerza de denoising (0-1)        var vocoderMix: Float = 0.0f,        // Blend vocoder/iSTFT (0-1)
        var targetAlignment: Float = 0.0f    // Fuerza de alineación a target (0-1)
    )
    
    private var params = OmegaParams()
    
    fun setSwdProjections(n: Int) {
        params.swdProjections = n.coerceIn(16, 128)
        // TODO: Enviar al motor nativo
        Log.d(TAG, "SWD projections: ${params.swdProjections}")
    }
    
    fun setPhaseCoherence(strength: Float) {
        params.phaseCoherence = strength.coerceIn(0f, 1f)
        Log.d(TAG, "Phase coherence: ${params.phaseCoherence}")
    }
    
    fun setCollapseStrength(strength: Float) {
        params.collapseStrength = strength.coerceIn(0f, 1f)
        Log.d(TAG, "Collapse strength: ${params.collapseStrength}")
    }
    
    fun setVocoderMix(mix: Float) {
        params.vocoderMix = mix.coerceIn(0f, 1f)
        Log.d(TAG, "Vocoder mix: ${params.vocoderMix}")
    }
    
    fun setTargetAlignment(strength: Float) {
        params.targetAlignment = strength.coerceIn(0f, 1f)
        Log.d(TAG, "Target alignment: ${params.targetAlignment}")
    }
    
    fun getParams(): OmegaParams = params
    
    // ═══════════════════════════════════════════════════════════════════════
    // MÉTRICAS EN TIEMPO REAL (para visualizadores)
    // ═══════════════════════════════════════════════════════════════════════
    
    data class OmegaMetrics(
        val latencyMs: Float,
        val swdCost: Float,
        val phaseCoherence: Float,
        val cpuUsage: Float,
        val npuUsage: Float,
        val gpuUsage: Float
    )
    
    fun getMetrics(): OmegaMetrics {
        return OmegaMetrics(
            latencyMs = getLatencyMs(),            swdCost = 0f,  // TODO: Obtener del motor nativo
            phaseCoherence = params.phaseCoherence,
            cpuUsage = 0f,  // TODO: Obtener de ThermalMonitor
            npuUsage = 0f,
            gpuUsage = 0f
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PRESETS (configuraciones predefinidas)
    // ═══════════════════════════════════════════════════════════════════════
    
    enum class Preset(val displayName: String, val description: String) {
        BYPASS("Bypass", "Sin procesamiento, solo routing"),
        PHASE_LOCK("Phase Lock", "Máxima coherencia de fase"),
        TRANSPORT("Transport", "Alineación espectral fuerte"),
        DENOISE("Denoise", "Colapso de ruido agresivo"),
        VOCODER("Vocoder", "Síntesis neural completa"),
        BALANCED("Balanced", "Configuración equilibrada")
    }
    
    fun applyPreset(preset: Preset) {
        when (preset) {
            Preset.BYPASS -> {
                setSwdProjections(16)
                setPhaseCoherence(0f)
                setCollapseStrength(0f)
                setVocoderMix(0f)
                setTargetAlignment(0f)
            }
            Preset.PHASE_LOCK -> {
                setSwdProjections(32)
                setPhaseCoherence(1f)
                setCollapseStrength(0.3f)
                setVocoderMix(0f)
                setTargetAlignment(0.5f)
            }
            Preset.TRANSPORT -> {
                setSwdProjections(128)
                setPhaseCoherence(0.6f)
                setCollapseStrength(0.4f)
                setVocoderMix(0f)
                setTargetAlignment(1f)
            }
            Preset.DENOISE -> {
                setSwdProjections(48)
                setPhaseCoherence(0.5f)
                setCollapseStrength(1f)
                setVocoderMix(0f)
                setTargetAlignment(0.3f)            }
            Preset.VOCODER -> {
                setSwdProjections(64)
                setPhaseCoherence(0.7f)
                setCollapseStrength(0.6f)
                setVocoderMix(1f)
                setTargetAlignment(0.8f)
            }
            Preset.BALANCED -> {
                setSwdProjections(64)
                setPhaseCoherence(0.8f)
                setCollapseStrength(0.5f)
                setVocoderMix(0f)
                setTargetAlignment(0.5f)
            }
        }
        Log.i(TAG, "Applied preset: ${preset.name}")
    }
}
