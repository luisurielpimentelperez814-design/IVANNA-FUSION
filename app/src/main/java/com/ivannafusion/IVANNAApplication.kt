package com.ivannafusion

import android.app.Application
import android.util.Log
import com.ivannafusion.persistence.ParameterStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
        lateinit var parameterStore: ParameterStore
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var isInitialized = false
            private set

        // Bridge singleton — accesible desde la UI
        val omegaBridge = OmegaEngineBridge()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== IVANNA DSP Application iniciada ===")

        parameterStore = ParameterStore(this)
        Log.d(TAG, "✅ ParameterStore creado")

        appScope.launch {
            try {
                Log.d(TAG, "🔄 DSPState.initialize...")
                DSPState.initialize(parameterStore)
                Log.d(TAG, "✅ DSPState.initialize OK")

                Log.d(TAG, "🔄 detectRealHardwareCapabilities...")
                DSPState.detectRealHardwareCapabilities(this@IVANNAApplication)
                Log.d(TAG, "✅ detectRealHardwareCapabilities OK")

                Log.d(TAG, "🔄 com.ivannafusion.dsp.DSPState...")
                com.ivannafusion.dsp.DSPState.initialize(this@IVANNAApplication)
                Log.d(TAG, "✅ com.ivannafusion.dsp.DSPState OK")

                Log.d(TAG, "🔄 ShmManager...")
                ShmManager.initialize(this@IVANNAApplication)
                Log.d(TAG, "✅ ShmManager OK")

                Log.d(TAG, "🔄 ThermalMonitor...")
                ThermalMonitor.initialize(this@IVANNAApplication)
                Log.d(TAG, "✅ ThermalMonitor OK")

                // Arrancar el daemon JNI (es no-op si el standalone de Magisk
                // ya está corriendo — solo conecta el socket).
                Log.d(TAG, "🔄 OmegaDaemon.start()...")
                val daemonOk = OmegaDaemon.start()
                Log.d(TAG, if (daemonOk) "✅ OmegaDaemon iniciado" else "⚠️ OmegaDaemon no disponible (¿Magisk standalone corriendo?)")

                // Pequeña espera para que el socket esté listo antes de conectar
                delay(300)

                Log.d(TAG, "🔄 OmegaEngineBridge.connect()...")
                omegaBridge.connect()
                Log.d(TAG, "✅ OmegaEngineBridge conectado (auto-retry en background)")

                isInitialized = true
                Log.i(TAG, "✅✅✅ TODOS LOS MOTORES INICIADOS ✅✅✅")

            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Error librería nativa: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error general: ${e.message}", e)
            }
        }
    }

    override fun onTerminate() {
        omegaBridge.disconnect()
        OmegaDaemon.stop()
        super.onTerminate()
    }
}
