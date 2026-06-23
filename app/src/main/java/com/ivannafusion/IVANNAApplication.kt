package com.ivannafusion

import android.app.Application
import android.util.Log
import com.ivannafusion.persistence.ParameterStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
        lateinit var parameterStore: ParameterStore
            private set
        
        // Hilo de trabajo en segundo plano para no bloquear la UI
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IVANNA DSP Application iniciada")
        
        parameterStore = ParameterStore(this)
        
        // FIX ANR: Todo lo que toca Root, Nativo o Hardware se manda a segundo plano
        appScope.launch {
            try {
                Log.d(TAG, "Iniciando motores en segundo plano...")
                DSPState.initialize(parameterStore)
                DSPState.detectRealHardwareCapabilities(this@IVANNAApplication)
                com.ivannafusion.dsp.DSPState.initialize(this@IVANNAApplication)
                
                ShmManager.initialize(this@IVANNAApplication)
                ThermalMonitor.initialize(this@IVANNAApplication)
                
                Log.i(TAG, "✅ ShmManager + ThermalMonitor + DSPState iniciados en segundo plano")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error de librería nativa (C++)", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error general en inicialización", e)
            }
        }
    }
}
