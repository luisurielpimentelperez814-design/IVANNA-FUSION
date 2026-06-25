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
 *
 * ===== NUEVO MÓDULO ESPACIAL Ω-ATLAS (añadido sin borrar nada) =====
 * Se han añadido funciones para el motor de audio espacial basado en la
 * ecuación de equilibrio triádico: p* = (n + μ·Ω(p*))/(1+μ)
 * Las implementaciones nativas están en spatial_engine.cpp y se compilan
 * en el mismo target ivanna_jni.
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

    // ======================================================================
    // 🆕 NUEVAS FUNCIONES PARA MOTOR ESPACIAL Ω-ATLAS (a partir de aquí)
    // ======================================================================

    /**
     * Inicializa el motor espacial con los parámetros dados.
     * @param sampleRate Frecuencia de muestreo (ej. 48000)
     * @param bufferSize Tamaño del bloque de procesamiento (ej. 64)
     * @return true si la inicialización fue exitosa
     */
    external fun nativeInitSpatialEngine(sampleRate: Int, bufferSize: Int): Boolean

    /**
     * Renderiza un bloque de audio espacializado a partir de un objeto de audio.
     * @param inputBuffer PCM mono de entrada (tamaño bufferSize)
     * @param outL Buffer de salida izquierdo (tamaño bufferSize)
     * @param outR Buffer de salida derecho (tamaño bufferSize)
     * @param posX Posición X del objeto (escala -100 a 100)
     * @param posY Posición Y del objeto (escala -100 a 100)
     * @param posZ Posición Z del objeto (escala -100 a 100)
     * @param mu Factor de consenso (0 a 1000)
     * @return número de muestras procesadas, o -1 si error
     */
    external fun nativeRenderSpatialBlock(
        inputBuffer: FloatArray,
        outL: FloatArray,
        outR: FloatArray,
        posX: Int,
        posY: Int,
        posZ: Int,
        mu: Int
    ): Int

    /**
     * Actualiza dinámicamente el factor μ (consenso) según errores perceptuales.
     * @param spatialErr Error espacial acumulado (ej. 0-100)
     * @param roomErr Error de sala (0-100)
     * @param maskingErr Error de enmascaramiento (0-100)
     * @return nuevo valor de μ (0-1000)
     */
    external fun nativeUpdateMu(spatialErr: Int, roomErr: Int, maskingErr: Int): Int

    /**
     * Libera los recursos del motor espacial.
     * @return true si se liberó correctamente
     */
    external fun nativeReleaseSpatialEngine(): Boolean

    /**
     * Carga un preset de configuración espacial (parámetros de HRTF, room, etc.)
     * @param presetName Nombre del preset (ej. "Studio", "Live")
     * @return true si se cargó correctamente
     */
    external fun nativeLoadSpatialPreset(presetName: String): Boolean

    /**
     * Guarda el preset espacial actual.
     * @param presetName Nombre del preset
     * @return true si se guardó correctamente
     */
    external fun nativeSaveSpatialPreset(presetName: String): Boolean

    /**
     * Obtiene el estado actual del motor espacial (parámetros en JSON).
     * @return String con los parámetros (ej. {"mu":500,"roomDecay":0.8})
     */
    external fun nativeGetSpatialState(): String

    /**
     * Establece parámetros del motor espacial desde un JSON.
     * @param params String JSON con los parámetros
     * @return true si se aplicaron correctamente
     */
    external fun nativeSetSpatialParams(params: String): Boolean
}
