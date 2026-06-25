package com.ivannafusion

/**
 * IvannaNativeLib — bindings JNI para evolutionary_kernel.cpp y phase_oracle.cpp.
 *
 * CORRECCIÓN: antes intentaba cargar "ivanna_native", librería que no existe
 * como target en ningún CMakeLists.txt del proyecto → UnsatisfiedLinkError
 * garantizado en el init{} antes de que ninguna external fun pudiera ejecutarse.
 *
 * Los símbolos Java_com_ivannafusion_IvannaNativeLib_* viven en:
 *   evolutionary_kernel.cpp → nativeInitializeEvolution / nativeGetBestFitness /
 *                             nativeGetGeneration / nativeEvolveStep
 *   phase_oracle.cpp        → nativePredictSamples / nativeGetPhaseState /
 *                             nativeSetPhaseParameters
 * Ambos archivos se compilaron en CMakeLists.txt como parte del target ivanna_jni.
 *
 * Las funciones que aún no tienen implementación nativa real (Audio Engine
 * duplicado, Preset, AI) están marcadas y tienen stubs en jni_wrapper.cpp
 * que devuelven valores seguros para evitar UnsatisfiedLinkError.
 */
object IvannaNativeLib {
    init {
        try {
            System.loadLibrary("ivanna_jni")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("IvannaNativeLib", "Failed to load ivanna_jni", e)
        }
    }

    // ===== EVOLUTIONARY KERNEL (implementado en evolutionary_kernel.cpp) =====
    external fun nativeInitializeEvolution(populationSize: Int, generations: Int): Boolean
    external fun nativeGetBestFitness(): Double
    external fun nativeGetGeneration(): Int
    external fun nativeEvolveStep(): Boolean

    // ===== PHASE ORACLE (implementado en phase_oracle.cpp) =====
    external fun nativePredictSamples(audioBuffer: FloatArray, sampleCount: Int): FloatArray
    external fun nativeGetPhaseState(): Float
    external fun nativeSetPhaseParameters(alpha: Float, beta: Float, gamma: Float): Boolean

    // ===== AUDIO ENGINE BRIDGE — stubs en jni_wrapper.cpp =====
    // Nota: AudioEngine.kt ya maneja el motor de audio vía sus propias external fun;
    // estas son un segundo punto de entrada alternativo. Implementaciones reales
    // pendientes — mientras tanto los stubs devuelven valores inocuos.
    external fun nativeInitAudioEngine(sampleRate: Int, bufferSize: Int): Boolean
    external fun nativeProcessAudio(inputBuffer: FloatArray, outputBuffer: FloatArray): Int
    external fun nativeReleaseAudioEngine(): Boolean

    // ===== PRESET / PERSISTENCE — stubs en jni_wrapper.cpp =====
    external fun nativeLoadPreset(presetName: String): Boolean
    external fun nativeSavePreset(presetName: String): Boolean
    external fun nativeGetCurrentParams(): String
    external fun nativeSetParams(params: String): Boolean

    // ===== AI ENGINE — stubs en jni_wrapper.cpp =====
    external fun nativeInitializeAI(modelPath: String): Boolean
    external fun nativeInferenceAI(inputData: FloatArray): FloatArray
    external fun nativeReleaseAI(): Boolean
}
