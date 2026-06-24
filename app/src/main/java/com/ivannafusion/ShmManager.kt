package com.ivannafusion

import android.content.Context
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()
    
    private var hyperplaneBuffer: MappedByteBuffer? = null
    private var shmFile: RandomAccessFile? = null
    
    var nativeLibLoaded: Boolean = false
        private set
    var shmInitialized: Boolean = false
        private set
    var lastError: String? = null
        private set

    // CORRECCIÓN: Usar el mismo path que omega_daemon.cpp
    private const val SHM_PATH = "/data/local/tmp/omega_shared_mem"
    private const val SHM_SIZE = 2 * 1024 * 1024

    // Offsets (deben coincidir con audio_orchestrator.cpp Hyperplane struct)
    private const val OFFSET_BIQUAD    = 0
    private const val OFFSET_KALMAN    = OFFSET_BIQUAD + 1280
    private const val OFFSET_POBLACION = OFFSET_KALMAN + 12
    private const val OFFSET_TEMP      = OFFSET_POBLACION + 32768
    private const val OFFSET_SCHED     = OFFSET_TEMP + 20
    private const val OFFSET_SEQ       = OFFSET_SCHED + 3072
    private const val OFFSET_ACTIVE    = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        _shmStatus.value = "Abriendo shared memory file..."
        try {
            // CORRECCIÓN: Usar file mapping en lugar de SharedMemory API
            val file = File(SHM_PATH)
            
            // Crear el archivo si no existe (requiere root)
            if (!file.exists()) {
                try {
                    // Intentar crear con permisos de root
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "touch $SHM_PATH && chmod 666 $SHM_PATH"))
                    process.waitFor()
                } catch (e: Exception) {
                    Log.w("IVANNA-SHM", "No se puede crear archivo SHM (requiere root): ${e.message}")
                }
            }
            
            // Abrir el archivo
            shmFile = RandomAccessFile(file, "rw")
            shmFile?.setLength(SHM_SIZE.toLong())
            
            // Mapear el archivo en memoria
            val channel = shmFile?.channel
            hyperplaneBuffer = channel?.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
            hyperplaneBuffer?.order(ByteOrder.nativeOrder())
            
            // Inicializar a cero
            hyperplaneBuffer?.position(0)
            repeat(SHM_SIZE / 4) { hyperplaneBuffer?.putInt(0) }
            hyperplaneBuffer?.position(0)
            
            shmInitialized = true
            _shmStatus.value = "SHM file mapping OK ✅"
            Log.i("IVANNA-SHM", "SharedMemory file mapping OK path=$SHM_PATH size=$SHM_SIZE")
            
        } catch (e: Exception) {
            lastError = e.message
            _shmStatus.value = "Fallback buffer directo 🟠"
            Log.w("IVANNA-SHM", "File mapping falló: ${e.message}, usando buffer directo")
            
            // Fallback: buffer directo en memoria
            val buf = ByteBuffer.allocateDirect(SHM_SIZE)
            buf.order(ByteOrder.nativeOrder())
            hyperplaneBuffer = buf as MappedByteBuffer?
            shmInitialized = true
        }
    }

    private external fun nativeMlock(address: Long, length: Long): Int

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer

    private fun buf(): ByteBuffer? = hyperplaneBuffer?.duplicate()?.order(ByteOrder.nativeOrder())

    fun readSeqCounter(): Long = buf()?.getLong(OFFSET_SEQ) ?: 0L
    fun readActiveBuffer(): Int = buf()?.get(OFFSET_ACTIVE)?.toInt()?.and(0xFF) ?: 0

    fun readKalmanState(): FloatArray {
        val b = buf() ?: return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            b.getFloat(OFFSET_KALMAN),
            b.getFloat(OFFSET_KALMAN + 4),
            b.getFloat(OFFSET_KALMAN + 8)
        )
    }

    fun readBiquadCoefs(): Array<IntArray> {
        val b = buf() ?: return Array(64) { IntArray(5) }
        val result = Array(64) { IntArray(5) }
        for (i in 0 until 64) for (j in 0 until 5)
            result[i][j] = b.getInt(OFFSET_BIQUAD + (i * 5 + j) * 4)
        return result
    }

    fun readTemperatures(): ShortArray {
        val b = buf() ?: return ShortArray(10)
        return ShortArray(10) { b.getShort(OFFSET_TEMP + it * 2) }
    }

    var kalman_fase_rad: Float = 0f
        private set
    var kalman_frec_hz: Float = 0f
        private set
    var shm_seq_counter: Long = 0L
        private set
    var shm_buffer_activo: Int = 0
        private set

    private fun smoothForDisplay(prev: Float, raw: Float, mu: Float = 0.3f): Float {
        if (raw.isNaN() || raw.isInfinite()) return prev
        if (prev.isNaN() || prev.isInfinite()) return raw
        return (prev + mu * raw) / (1.0f + mu)
    }

    fun refreshCanonicalVars() {
        val b = buf() ?: return
        kalman_fase_rad   = smoothForDisplay(kalman_fase_rad, b.getFloat(OFFSET_KALMAN))
        kalman_frec_hz    = smoothForDisplay(kalman_frec_hz, b.getFloat(OFFSET_KALMAN + 4))
        shm_seq_counter   = b.getLong(OFFSET_SEQ)
        shm_buffer_activo = b.get(OFFSET_ACTIVE).toInt().and(0xFF)
    }

    fun writeFusionLevel(level: Float) {
        hyperplaneBuffer?.putFloat(OFFSET_KALMAN + 12, level)
    }

    fun writeTemperatures(temps: ShortArray) {
        val b = hyperplaneBuffer ?: return
        val count = minOf(temps.size, 10)
        for (i in 0 until count) {
            b.putShort(OFFSET_TEMP + i * 2, temps[i])
        }
    }

    fun close() {
        _shmStatus.value = "SHM cerrada"
        hyperplaneBuffer = null
        shmFile?.close()
        shmFile = null
        shmInitialized = false
    }

    init {
        try {
            System.loadLibrary("ivanna_jni")
            nativeLibLoaded = true
            _shmStatus.value = "Librería nativa cargada"
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            lastError = e.message
            _shmStatus.value = "Error librería nativa: ${e.message}"
        }
    }
}
