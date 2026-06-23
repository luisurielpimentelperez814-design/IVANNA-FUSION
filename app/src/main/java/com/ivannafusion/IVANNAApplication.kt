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
        com.ivannafusion.dsp.DSPState.initialize(this)  // SharedPreferences para DashboardScreen
        ShmManager.initialize(this)
        ThermalMonitor.initialize(this)
        Log.i(TAG, "✅ ShmManager + ThermalMonitor + DSPState iniciados")
    }
}
