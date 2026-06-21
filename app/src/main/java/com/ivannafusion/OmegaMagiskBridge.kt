package com.ivannafusion

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

data class TelemetryData(
    val temperature: Float = 35.0f,
    val npuUsage: Float = 0.0f,
    val latencyMs: Float = 0.0f,
    val isConnected: Boolean = false
)

class OmegaMagiskBridge {

    companion object {
        private const val TAG = "OmegaMagiskBridge"
        private const val SOCKET_NAME = "omega_daemon_socket"
        private const val POLL_INTERVAL_MS = 1000L
    }

    // Variables de estado
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private var socket: LocalSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var scope: CoroutineScope? = null

    fun connect() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope?.launch {
            try {
                socket = LocalSocket()
                socket?.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                writer = PrintWriter(socket!!.outputStream, true)
                reader = BufferedReader(InputStreamReader(socket!!.inputStream))
                
                _isConnected.value = true
                Log.i(TAG, "Conectado al daemon de Magisk")
                
                startTelemetryPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando al daemon: ${e.message}")
                _isConnected.value = false
            }
        }
    }

    fun disconnect() {
        scope?.cancel()
        try {
            reader?.close()
            writer?.close()
            socket?.close()
            _isConnected.value = false
            Log.i(TAG, "Desconectado del daemon")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar: ${e.message}")
        }
    }

    private fun sendCommand(command: String) {
        if (!_isConnected.value || writer == null) {
            Log.w(TAG, "No conectado al daemon")
            return
        }
        
        try {
            writer?.println(command)
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando comando: ${e.message}")
        }
    }

    fun setProcessingState(enabled: Boolean) {
        sendCommand("SET_PROCESSING:${if (enabled) "1" else "0"}")
    }

    fun setVocoderMix(mix: Float) {
        sendCommand("SET_VOCODER_MIX:$mix")
    }

    fun setPreset(presetName: String) {
        sendCommand("SET_PRESET:$presetName")
    }

    fun setIntensity(level: Float) {
        sendCommand("SET_INTENSITY:$level")
    }

    fun enableBypass(enabled: Boolean) {
        sendCommand("SET_BYPASS:${if (enabled) "1" else "0"}")
    }

    fun setThermalThrottle(enabled: Boolean) {
        sendCommand("SET_THERMAL_THROTTLE:${if (enabled) "1" else "0"}")
    }

    fun resetToDefaults() {
        sendCommand("RESET_DEFAULTS")
    }

    private fun startTelemetryPolling() {
        scope?.launch {
            while (isActive && _isConnected.value) {
                try {
                    sendCommand("GET_TELEMETRY")
                    val response = reader?.readLine()
                    if (response != null) {
                        parseTelemetry(response)
                    }
                    delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en polling de telemetría: ${e.message}")
                    _isConnected.value = false
                    break
                }
            }
        }
    }

    private fun parseTelemetry(jsonLine: String) {
        // Parseo simple (en producción usa Gson/Moshi)
        try {
            val temp = extractFloatValue(jsonLine, "temp") ?: 35.0f
            val npu = extractFloatValue(jsonLine, "npu") ?: 0.0f
            val latency = extractFloatValue(jsonLine, "latency") ?: 0.0f
            
            _telemetry.value = TelemetryData(
                temperature = temp,
                npuUsage = npu,
                latencyMs = latency,
                isConnected = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando telemetría: ${e.message}")
        }
    }

    private fun extractFloatValue(json: String, key: String): Float? {
        val regex = "\"$key\"\\s*:\\s*([0-9.]+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull()
    }
}
