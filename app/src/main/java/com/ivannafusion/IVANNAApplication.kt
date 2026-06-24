package com.ivannafusion

import android.app.Application
import android.util.Log
import com.ivannafusion.persistence.ParameterStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
        lateinit var parameterStore: ParameterStore
            private set
        
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var isInitialized = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== IVANNA DSP Application iniciada ===")
        
        parameterStore = ParameterStore(this)
        Log.d(TAG, "✅ ParameterStore creado")
        
        appScope.launch {
            try {
                Log.d(TAG, "🔄 Iniciando DSPState.initialize...")
                DSPState.initialize(parameterStore)
                Log.d(TAG, "✅ DSPState.initialize completado")
                
                Log.d(TAG, "🔄 Iniciando detectRealHardwareCapabilities...")
                DSPState.detectRealHardwareCapabilities(this@IVANNAApplication)
                Log.d(TAG, "✅ detectRealHardwareCapabilities completado")
                
                Log.d(TAG, "🔄 Iniciando com.ivannafusion.dsp.DSPState...")
                com.ivannafusion.dsp.DSPState.initialize(this@IVANNAApplication)
                Log.d(TAG, "✅ com.ivannafusion.dsp.DSPState completado")
                
                Log.d(TAG, "🔄 Iniciando ShmManager (puede tardar)...")
                ShmManager.initialize(this@IVANNAApplication)
                Log.d(TAG, "✅ ShmManager completado")
                
                Log.d(TAG, "🔄 Iniciando ThermalMonitor...")
                ThermalMonitor.initialize(this@IVANNAApplication)
                Log.d(TAG, "✅ ThermalMonitor completado")
                
                isInitialized = true
                Log.i(TAG, "✅✅✅ TODOS LOS MOTORES INICIADOS CORRECTAMENTE ✅✅✅")
                
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Error de librería nativa (C++): ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error general: ${e.message}", e)
            }
        }
    }
}
