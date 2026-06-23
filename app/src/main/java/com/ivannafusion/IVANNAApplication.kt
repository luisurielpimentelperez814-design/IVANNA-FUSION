package com.ivannafusion

import android.app.Application
import android.util.Log
import com.ivannafusion.persistence.ParameterStore

class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
        lateinit var parameterStore: ParameterStore
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IVANNA DSP Application iniciada")
        parameterStore = ParameterStore(this)
        DSPState.initialize(parameterStore)
        DSPState.detectRealHardwareCapabilities(this)
        com.ivannafusion.dsp.DSPState.initialize(this)
        // NOTA: com.ivannafusion.dsp.DSPState (paquete 'dsp') es una
        // implementación paralela del mismo concepto (persistencia de
        // controles), escrita en otra sesión y verificada: NINGÚN
        // archivo de ui/screens la importa actualmente. La fuente real
        // en uso es com.ivannafusion.DSPState (sin paquete 'dsp', arriba),
        // que ya usan EffectsScreen/AIScreen/PFEngineScreen. Se mantiene
        // esta línea para no perder su estado por si algo la referencia
        // en el futuro, pero no es la fuente de verdad activa.
        ShmManager.initialize(this)
        ThermalMonitor.initialize(this)
        Log.i(TAG, "✅ ShmManager + ThermalMonitor + DSPState iniciados")
    }
}
