package com.ivannafusion

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Puente de comunicación entre la APK y el daemon Ω_in (root).
 * Se comunica vía Unix Domain Socket con el daemon iniciado por Magisk.
 */
object OmegaMagiskBridge {
    private const val TAG = "OmegaMagiskBridge"
    private const val SOCKET_PATH = "omega_control"
    private const val SOCKET_PATH_ABS = "/data/omega/control.sock"
    private const val POLL_INTERVAL_MS = 500L

    // ═══════════════════════════════════════════════════════════
    // TELEMETRÍA (StateFlow para UI reactiva)
    // ═══════════════════════════════════════════════════════════
    data class TelemetryData(
        val temperature: Float = 0f,
        val latencyMs: Float = 0f,
        val npuUsage: Int = 0,
        val isThrottling: Boolean = false,
        val isDaemonRunning: Boolean = false,
        val processedBlocks: Int = 0,
        val droppedBlocks: Int = 0
    )

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    // ESTADO DE CONEXIÓN
    // ═══════════════════════════════════════════════════════════
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private var socket: LocalSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ═══════════════════════════════════════════════════════════
    // CONEXIÓN AL SOCKET
    // ═══════════════════════════════════════════════════════════
    fun connect() {
        if (_isConnected.value) {
            Log.w(TAG, "Already connected")
            return
        }

        scope.launch {
            try {
                val localSocket = LocalSocket()
                localSocket.connect(
                    LocalSocketAddress(
                        SOCKET_PATH_ABS,
                        LocalSocketAddress.Namespace.FILESYSTEM
                    )
                )
                socket = localSocket
                _isConnected.value = true
                Log.i(TAG, "Connected to daemon socket")

                // Iniciar polling de telemetría
                startTelemetryPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to daemon: ${e.message}")
                _isConnected.value = false
                _telemetry.value = _telemetry.value.copy(isDaemonRunning = false)
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        _isConnected.value = false
        Log.i(TAG, "Disconnected from daemon")
    }

    // ═══════════════════════════════════════════════════════════
    // ENVÍO DE COMANDOS
    // ═══════════════════════════════════════════════════════════    fun sendCommand(command: String): String {
        val currentSocket = socket
        if (currentSocket == null || !currentSocket.isConnected) {
            Log.e(TAG, "Cannot send command: not connected")
            return ""
        }

        return try {
            val writer = OutputStreamWriter(currentSocket.outputStream)
            writer.write(command)
            writer.flush()

            val reader = BufferedReader(InputStreamReader(currentSocket.inputStream))
            val response = reader.readLine() ?: ""
            Log.d(TAG, "Command: $command -> Response: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command '$command': ${e.message}")
            _isConnected.value = false
            ""
        }
    }

    // ═══════════════════════════════════════════════════════════
    // API DE CONTROL (para la UI)
    // ═══════════════════════════════════════════════════════════
    fun setBypass(enabled: Boolean) {
        sendCommand("bypass=${if (enabled) 1 else 0}")
    }

    fun setIntensity(value: Float) {
        sendCommand("intensity=${value.coerceIn(0f, 1f)}")
    }

    fun setPreset(presetId: Int) {
        sendCommand("preset=$presetId")
    }

    fun setSwdProjections(count: Int) {
        sendCommand("swd_proj=${count.coerceIn(16, 128)}")
    }

    fun setPhaseCoherence(value: Float) {
        sendCommand("phase=${value.coerceIn(0f, 1f)}")
    }

    fun setCollapseStrength(value: Float) {
        sendCommand("collapse=${value.coerceIn(0f, 1f)}")
    }
    fun setVocoderMix(value: Float) {
        sendCommand("vocoder=${value.coerceIn(0f, 1f)}")
    }

    // ═══════════════════════════════════════════════════════════
    // POLLING DE TELEMETRÍA
    // ═══════════════════════════════════════════════════════════
    private fun startTelemetryPolling() {
        scope.launch {
            while (isActive && _isConnected.value) {
                try {
                    val response = sendCommand("status")
                    if (response.isNotEmpty()) {
                        val parsed = parseTelemetry(response)
                        _telemetry.value = parsed
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Telemetry poll error: ${e.message}")
                    _isConnected.value = false
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun parseTelemetry(raw: String): TelemetryData {
        var temp = 0f
        var lat = 0f
        var npu = 0
        var throttle = false
        var blocks = 0
        var dropped = 0

        val parts = raw.split(";")
        for (part in parts) {
            val kv = part.split("=", limit = 2)
            if (kv.size != 2) continue
            when (kv[0].trim()) {
                "temp" -> temp = kv[1].toFloatOrNull() ?: 0f
                "lat" -> lat = kv[1].toFloatOrNull() ?: 0f
                "npu" -> npu = kv[1].toIntOrNull() ?: 0
                "throttle" -> throttle = kv[1].trim() == "1"
                "blocks" -> blocks = kv[1].toIntOrNull() ?: 0
                "dropped" -> dropped = kv[1].toIntOrNull() ?: 0
            }
        }

        return TelemetryData(
            temperature = temp,            latencyMs = lat,
            npuUsage = npu,
            isThrottling = throttle,
            isDaemonRunning = true,
            processedBlocks = blocks,
            droppedBlocks = dropped
        )
    }
}
