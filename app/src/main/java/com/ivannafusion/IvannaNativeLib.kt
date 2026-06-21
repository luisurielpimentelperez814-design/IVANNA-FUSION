package com.ivannafusion

/**
 * Interfaz completa de JNI para librerias nativas.
 * Conecta Kotlin con las funciones C++ en evolutionary_kernel.cpp y phase_oracle.cpp
 */
object IvannaNativeLib {
    init {
        try {
            System.loadLibrary("ivanna_native")
            System.loadLibrary("pf_engine")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("IvannaNativeLib", "Failed to load native libraries", e)
        }
    }

    // ===== EVOLUTIONARY KERNEL BINDINGS =====
    external fun nativeInitializeEvolution(populationSize: Int, generations: Int): Boolean
    
    external fun nativeGetBestFitness(): Double
    
    external fun nativeGetGeneration(): Int
    
    external fun nativeEvolveStep(): Boolean

    // ===== PHASE ORACLE BINDINGS =====
    external fun nativePredictSamples(audioBuffer: FloatArray, sampleCount: Int): FloatArray
    
    external fun nativeGetPhaseState(): Float
    
    external fun nativeSetPhaseParameters(alpha: Float, beta: Float, gamma: Float): Boolean

    // ===== AUDIO ENGINE BINDINGS =====
    external fun nativeInitAudioEngine(sampleRate: Int, bufferSize: Int): Boolean
    
    external fun nativeProcessAudio(inputBuffer: FloatArray, outputBuffer: FloatArray): Int
    
    external fun nativeReleaseAudioEngine(): Boolean

    // ===== PRESET & PERSISTENCE =====
    external fun nativeLoadPreset(presetName: String): Boolean
    
    external fun nativeSavePreset(presetName: String): Boolean
    
    external fun nativeGetCurrentParams(): String
    
    external fun nativeSetParams(params: String): Boolean

    // ===== AI ENGINE BINDINGS =====
    external fun nativeInitializeAI(modelPath: String): Boolean
    
    external fun nativeInferenceAI(inputData: FloatArray): FloatArray
    
    external fun nativeReleaseAI(): Boolean
}
