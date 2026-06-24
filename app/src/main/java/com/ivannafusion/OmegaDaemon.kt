package com.ivannafusion

import android.util.Log

/**
 * OmegaDaemon: Interfaz JNI para el daemon root de Magisk (omega_daemon.cpp)
 * 
 * Este objeto expone las funciones nativas para controlar el daemon que corre
 * con permisos root y se comunica vía Unix Domain Socket.
 */
object OmegaDaemon {
    private const val TAG = "OmegaDaemon"
    
    // Cargar la librería nativa
    init {
        try {
            System.loadLibrary("ivanna_jni")
            Log.i(TAG, "✅ Librería nativa ivanna_jni cargada")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ Error cargando librería nativa: ${e.message}")
        }
    }
    
    /**
     * Inicia el daemon de Magisk (omega_daemon)
     * 
     * Este daemon:
     * - Escucha comandos en Unix socket abstracto "omega_daemon_socket"
     * - Comparte memoria vía /data/local/tmp/omega_shared_mem
     * - Ejecuta inferencia de IA en NPU/GPU
     * - Maneja gestión térmica automáticamente
     * 
     * @return true si el daemon se inició correctamente, false en caso contrario
     */
    @JvmStatic
    external fun nativeStart(): Boolean
    
    /**
     * Detiene el daemon de Magisk
     */
    @JvmStatic
    external fun nativeStop()
    
    /**
     * Verifica si el daemon está corriendo
     */
    @JvmStatic
    external fun nativeIsRunning(): Boolean
}
