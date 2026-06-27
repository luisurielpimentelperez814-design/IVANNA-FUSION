package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ShmManager {
    private const val TAG = "IVANNA-SHM"
    private const val SHM_SIZE = 2 * 1024 * 1024

    private val _shmStatus = MutableStateFlow("Inicializando…")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()

    private var hyperplaneBuffer: ByteBuffer? = null
    private var sharedMemory: SharedMemory? = null

    var nativeLibLoaded: Boolean = false
        private set
    var shmInitialized: Boolean = false
        private set
    var lastError: String? = null
        private set

    // Offsets alineados con shm_hyperplane.cpp / audio_orchestrator.cpp
    private const val OFFSET_BIQUAD = 0
    private const val OFFSET_KALMAN = OFFSET_BIQUAD + 1280
    private const val OFFSET_POBLACION = OFFSET_KALMAN + 12
    private const val OFFSET_TEMP = OFFSET_POBLACION + 32768
    private const val OFFSET_SCHED = OFFSET_TEMP + 20
    private const val OFFSET_SEQ = OFFSET_SCHED + 3072
    private const val OFFSET_ACTIVE = OFFSET_SEQ + 8

    var kalman_fase_rad: Float = 0f
        private set
    var kalman_frec_hz: Float = 0f
        private set
    var shm_seq_counter: Long = 0L
        private set
    var shm_buffer_activo: Int = 0
        private set

    init {
        try {
            System.loadLibrary("ivanna_jni")
            nativeLibLoaded = true
            _shmStatus.value = "Librería SHM lista"
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            lastError = e.message
            _shmStatus.value = "JNI no disponible: ${e.message}"
            Log.w(TAG, "No se pudo cargar ivanna_jni para SHM", e)
        }
    }

    fun initialize(context: Context) {
        if (shmInitialized) return
        _shmStatus.value = "Creando SharedMemory…"
        try {
            val shm = SharedMemory.create("ivanna_hyperplane", SHM_SIZE)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            val mapped = shm.mapReadWrite().order(ByteOrder.nativeOrder())
            mapped.position(0)
            repeat(SHM_SIZE / 4) { mapped.putInt(0) }
            mapped.position(0)
            sharedMemory = shm
            hyperplaneBuffer = mapped

            if (nativeLibLoaded) {
                try {
                    val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                    addressField.isAccessible = true
                    val address = addressField.getLong(mapped)
                    nativeMlock(address, SHM_SIZE.toLong())
                } catch (e: Exception) {
                    Log.w(TAG, "mlock no disponible, continúo sin fijar páginas", e)
                }
            }

            shmInitialized = true
            lastError = null
            _shmStatus.value = "SHM real activa"
            Log.i(TAG, "SharedMemory creada correctamente size=$SHM_SIZE seq=$OFFSET_SEQ kalman=$OFFSET_KALMAN")
        } catch (e: Exception) {
            lastError = e.message
            _shmStatus.value = "Fallback directo"
            hyperplaneBuffer = ByteBuffer.allocateDirect(SHM_SIZE).order(ByteOrder.nativeOrder())
            shmInitialized = true
            Log.w(TAG, "SharedMemory falló, usando buffer directo", e)
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
        return Array(64) { i ->
            IntArray(5) { j -> b.getInt(OFFSET_BIQUAD + (i * 5 + j) * 4) }
        }
    }

    fun readTemperatures(): ShortArray {
        val b = buf() ?: return ShortArray(10)
        return ShortArray(10) { b.getShort(OFFSET_TEMP + it * 2) }
    }

    fun refreshCanonicalVars() {
        val b = buf() ?: return
        kalman_fase_rad = b.getFloat(OFFSET_KALMAN)
        kalman_frec_hz = b.getFloat(OFFSET_KALMAN + 4)
        shm_seq_counter = b.getLong(OFFSET_SEQ)
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
        sharedMemory?.close()
        sharedMemory = null
        shmInitialized = false
    }
}
