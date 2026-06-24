package com.ivannafusion

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * OmegaEngineBridge: Controla el daemon root de Magisk (omega_daemon) 
 * mediante Unix Domain Socket (abstract namespace) para el motor Omega_in.
 * 
 * CORRECCIÓN: Cambiado de TCP (127.0.0.1:8500) a Unix socket abstracto
 * para ser compatible con omega_daemon.cpp
 */
class OmegaEngineBridge {

    companion object {
        private const val TAG = "OmegaEngineBridge"
        private const val SOCKET_NAME = "omega_daemon_socket"
    }

    // --- VARIABLES DE ESTADO ---
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _vocoderMix = MutableStateFlow(0.8f) 
    val vocoderMix: StateFlow<Float> = _vocoderMix.asStateFlow()

    private val _deviceTemp = MutableStateFlow(35.0f)
    val deviceTemp: StateFlow<Float> = _deviceTemp.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: LocalSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var telemetryThread: Thread? = null
    private var isListening = false

    // --- MÉTODOS DE CONTROL ---

    fun connectToDaemon() {
        try {
            socket = LocalSocket()
            socket?.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            writer = PrintWriter(socket!!.outputStream, true)
            reader = BufferedReader(InputStreamReader(socket!!.inputStream))
            _isConnected.value = true
            Log.d(TAG, "✅ Conectado al daemon Omega vía Unix socket abstracto")
            startTelemetryListener()
        } catch (e: Exception) {
            _isConnected.value = false
            Log.e(TAG, "❌ Error conectando al daemon: ${e.message}")
        }
    }

    fun setProcessingState(state: Boolean) {
        _isProcessing.value = state
        sendCommandToDaemon("SET_STATE", if (state) "1" else "0")
    }

    fun setVocoderMix(mix: Float) {
        val clampedMix = mix.coerceIn(0f, 1f)
        _vocoderMix.value = clampedMix
        sendCommandToDaemon("SET_VOCODER_MIX", clampedMix.toString())
    }

    fun applyPreset(presetName: String) {
        Log.d(TAG, "Aplicando preset: $presetName")
        sendCommandToDaemon("SET_PRESET", presetName)
    }

    // --- COMUNICACIÓN CON EL DAEMON ---

    private fun sendCommandToDaemon(action: String, value: String) {
        try {
            if (writer == null || !_isConnected.value) {
                Log.w(TAG, "Daemon no conectado.")
                return
            }
            val payload = """{"action":"$action","value":"$value"}
"""
            writer?.print(payload)
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando comando: ${e.message}")
        }
    }

    private fun startTelemetryListener() {
        isListening = true
        telemetryThread = Thread {
            try {
                while (isListening) {
                    val line = reader?.readLine() ?: break
                    if (line.isNotEmpty()) {
                        parseTelemetry(line)
                    }
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e(TAG, "Hilo de telemetría cerrado: ${e.message}")
                }
            }
        }.apply {
            name = "Omega-Telemetry"
            isDaemon = true
            start()
        }
    }

    private fun parseTelemetry(jsonLine: String) {
        if (jsonLine.contains("temp")) {
            _deviceTemp.value = 38.5f // Placeholder de telemetría
        }
    }

    fun disconnect() {
        isListening = false
        _isConnected.value = false
        try {
            reader?.close()
            writer?.close()
            socket?.close()
            telemetryThread?.join(1000)
            telemetryThread = null
            Log.d(TAG, "Desconectado del daemon")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar: ${e.message}")
        }
    }
}
