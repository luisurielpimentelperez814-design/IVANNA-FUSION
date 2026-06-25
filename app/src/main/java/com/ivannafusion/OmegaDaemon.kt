package com.ivannafusion

import android.util.Log

/**
 * OmegaDaemon — wrapper JNI del daemon de audio (libomega_daemon.so).
 *
 * Se usa cuando el módulo Magisk NO está instalado (el ejecutable standalone
 * omega_daemon no corre). En ese caso la APK inicia el daemon dentro de su
 * propio proceso. Con Magisk instalado, el daemon standalone ya está corriendo
 * y nativeStart() detecta el socket activo sin conflicto.
 */
object OmegaDaemon {

    private const val TAG = "OmegaDaemon"
    private var loaded = false

    init {
        try {
            System.loadLibrary("omega_daemon")
            loaded = true
            Log.i(TAG, "libomega_daemon.so cargada")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "No se pudo cargar libomega_daemon.so: ${e.message}")
        }
    }

    fun start(): Boolean {
        if (!loaded) return false
        return nativeStart().also { ok ->
            if (ok) Log.i(TAG, "Daemon iniciado") else Log.e(TAG, "nativeStart() falló")
        }
    }

    fun stop() {
        if (loaded) nativeStop()
    }

    fun setProcessing(active: Boolean) { if (loaded) nativeSetProcessing(active) }
    fun setIntensity(v: Float)          { if (loaded) nativeSetIntensity(v) }
    fun getTemperature(): Float          = if (loaded) nativeGetTemperature() else 35f
    fun getLatency(): Float              = if (loaded) nativeGetLatency() else 0f

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeSetProcessing(active: Boolean)
    private external fun nativeSetIntensity(intensity: Float)
    private external fun nativeGetTemperature(): Float
    private external fun nativeGetLatency(): Float
}
