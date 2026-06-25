package com.ivannafusion

import android.util.Log

class OmegaEngineBridge {
    private val TAG = "OmegaEngineBridge"
    private var connected = false

    fun connect() {
        Log.d(TAG, "Conectando...")
        connected = true
    }

    fun disconnect() {
        Log.d(TAG, "Desconectando...")
        connected = false
    }

    fun isConnected(): Boolean = connected
}
