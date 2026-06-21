package com.ivannafusion

import android.app.Application
import android.util.Log
import timber.log.Timber

/**
 * Aplicación principal de IVANNA FUSION.
 * Inicializa logging y servicios globales.
 */
class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
        
        @Volatile
        private var instance: IVANNAApplication? = null
        
        fun getInstance(): IVANNAApplication {
            return instance ?: throw RuntimeException("IVANNAApplication no inicializada")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.d(TAG, "Inicializando IVANNA FUSION...")
        
        // Inicializar Timber para logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Log.d(TAG, "Timber Debug Tree plantado")
        }
        
        // Cargar librerías nativas
        try {
            Log.d(TAG, "Cargando librerías nativas...")
            System.loadLibrary("ivanna_native")
            System.loadLibrary("pf_engine")
            Log.d(TAG, "Librerías nativas cargadas exitosamente")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Error cargando librerías nativas", e)
            Timber.e(e, "Fatal: No se pudieron cargar librerías nativas")
        }
        
        Log.d(TAG, "IVANNA FUSION inicializada correctamente")
    }
}
