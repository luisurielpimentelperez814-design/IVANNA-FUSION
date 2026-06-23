package com.ivannafusion

import android.app.Application
import android.util.Log
import com.ivannafusion.dsp.DSPState

class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
    }

    override fun onCreate() {
        super.onCreate()
        // CORRECCIÓN DE CRASH: DSPState.initialize() nunca se llamaba
        // desde ningún punto de la app -> el primer slider que el
        // usuario tocara en EffectsScreen (setMasterVolume, setBassBoost,
        // etc.) lanzaba UninitializedPropertyAccessException sobre
        // 'prefs' (lateinit var), sin try/catch, crasheando la app justo
        // después de que el usuario interactuaba con la UI (coincide con
        // "crashea después de los permisos": los permisos son lo último
        // antes de que la UI con sliders se vuelva interactiva).
        DSPState.initialize(this)
        Log.d(TAG, "IVANNA Application iniciada (DSPState inicializado)")
    }
}
