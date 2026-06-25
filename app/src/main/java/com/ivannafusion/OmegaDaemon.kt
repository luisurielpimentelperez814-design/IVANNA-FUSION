package com.ivannafusion

import android.util.Log

/**
 * OmegaDaemon: Interfaz JNI para el daemon root de Magisk (omega_daemon.cpp)
 *
 * CORRECCIONES aplicadas en este commit:
 *   1. loadLibrary("omega_daemon") — antes cargaba "ivanna_jni", que compila
 *      jni_wrapper.cpp y NO tiene los símbolos Java_com_ivannafusion_OmegaDaemon_*.
 *      Los símbolos correctos están en omega_daemon.cpp → libomega_daemon.so.
 *   2. Quitado @JvmStatic de las external fun. Con @JvmStatic el runtime espera
 *      la firma JNI con jclass como segundo parámetro; omega_daemon.cpp usa jobject
 *      (instancia del singleton) → mismatch de firma → UnsatisfiedLinkError.
 *      Sin @JvmStatic la firma es jobject, que coincide con lo que está compilado.
 */
object OmegaDaemon {
    private const val TAG = "OmegaDaemon"

    init {
        try {
            System.loadLibrary("omega_daemon")
            Log.i(TAG, "✅ Librería nativa omega_daemon cargada")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ Error cargando omega_daemon (requiere root/Magisk): ${e.message}")
        }
    }

    /** Inicia el daemon root (Unix socket + ring buffer SPSC + inferencia NPU). */
    external fun nativeStart(): Boolean

    /** Detiene el daemon. */
    external fun nativeStop()

    /** Devuelve true si el daemon sigue corriendo. */
    external fun nativeIsRunning(): Boolean
}
