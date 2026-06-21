package com.ivannafusion

import android.app.Application
import android.util.Log

class IVANNAApplication : Application() {
    companion object {
        private const val TAG = "IVANNAApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IVANNA Application iniciada")
    }
}
