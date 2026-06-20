package com.ivannafusion

/**
 * Wrapper para las funciones nativas de IVANNA-FUSION.
 * Estas funciones se comunican con el código C++ en src/main/cpp/
 */
object IvannaNativeLib {
    
    init {
        try {
            System.loadLibrary("ivanna_trascendental")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("IvannaNativeLib", "No se pudo cargar la librería nativa: ${e.message}")
        }
    }
    
    // Funciones nativas declaradas en C++
    external fun nativeSetEnabled(enabled: Boolean)
    external fun nativeIsEnabled(): Boolean
    external fun nativeEqSetGain(band: Int, gain: Float)
    external fun nativeEqGetGain(band: Int): Float
    
    /**
     * Activa o desactiva el efecto de audio
     */
    fun setEnabled(enabled: Boolean) {
        try {
            nativeSetEnabled(enabled)
        } catch (e: Exception) {
            android.util.Log.e("IvannaNativeLib", "Error al setEnabled: ${e.message}")
        }
    }
    
    /**
     * Verifica si el efecto está activado
     */
    fun isEnabled(): Boolean {
        return try {
            nativeIsEnabled()
        } catch (e: Exception) {
            android.util.Log.e("IvannaNativeLib", "Error al isEnabled: ${e.message}")
            false
        }
    }
    
    /**
     * Ajusta la ganancia de una banda de ecualización
     * @param band Índice de la banda (0-9 para 10 bandas)
     * @param gain Ganancia en dB (-20 a +20)
     */
    fun eqSetGain(band: Int, gain: Float) {
        try {
            nativeEqSetGain(band, gain)
        } catch (e: Exception) {
            android.util.Log.e("IvannaNativeLib", "Error al eqSetGain: ${e.message}")
        }
    }
    
    /**
     * Obtiene la ganancia actual de una banda
     * @param band Índice de la banda
     * @return Ganancia en dB
     */
    fun eqGetGain(band: Int): Float {
        return try {
            nativeEqGetGain(band)
        } catch (e: Exception) {
            android.util.Log.e("IvannaNativeLib", "Error al eqGetGain: ${e.message}")
            0.0f
        }
    }
}
