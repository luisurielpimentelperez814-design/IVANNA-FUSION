package com.ivannafusion

import android.app.Application
import android.util.Log

class IVANNAApplication : Application() {
    companion object {
        lateinit var instance: IVANNAApplication
            private set
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("IVANNAApp", "Aplicación inicializada")
    }
}
