/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "IVANNA-SHM"

object ShmManager {
    var nativeLibLoaded = false
    var shmInitialized = false
    var lastError: String? = null

    private const val SHM_SIZE = 2 * 1024 * 1024
    private const val SHM_NAME = "ivanna_hyperplane"

    private var hyperplaneBuffer: MappedByteBuffer? = null
    private var fd: Int = -1

    private const val OFFSET_BIQUAD = 0
    private const val OFFSET_KALMAN = OFFSET_BIQUAD + (64 * 5 * 4)
    private const val OFFSET_POBLACION = OFFSET_KALMAN + (3 * 4)
    private const val OFFSET_TEMP = OFFSET_POBLACION + (128 * 256)
    private const val OFFSET_SCHED = OFFSET_TEMP + (10 * 2)
    private const val OFFSET_SEQ = OFFSET_SCHED + (8 * 8 * 4 * 4 * 3)
    private const val OFFSET_ACTIVE = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        Log.i(TAG, "Inicializando ShmManager...")
        try {
            fd = memfdCreate(SHM_NAME, OsConstants.MFD_ALLOW_SEALING or 0x00000004)
            if (fd < 0) {
                val shmFile = File("/dev/shm/$SHM_NAME")
                if (!shmFile.exists()) {
                    Runtime.getRuntime().exec("su -c \"mknod /dev/shm/$SHM_NAME p\"").waitFor()
                }
                val raf = RandomAccessFile(shmFile, "rw")
                raf.setLength(SHM_SIZE.toLong())
                hyperplaneBuffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
                raf.close()
            } else {
                Os.ftruncate(fd, SHM_SIZE.toLong())
                val raf = RandomAccessFile("/proc/self/fd/$fd", "rw")
                hyperplaneBuffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
                raf.close()
            }

            hyperplaneBuffer?.let { buf ->
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(buf)
                nativeMlock(address, SHM_SIZE.toLong())
            }
            shmInitialized = true
            Log.i(TAG, "ShmManager inicializado correctamente")

        } catch (e: Exception) {
            lastError = "SHM: ${e.message}"
            Log.e(TAG, "Error ShmManager: ${e.message}", e)
            hyperplaneBuffer = java.nio.ByteBuffer.allocateDirect(SHM_SIZE)
            shmInitialized = true // Fallback funciona
        }
    }

    private external fun nativeMlock(address: Long, length: Long): Int
    private external fun memfdCreate(name: String, flags: Int): Int

    fun getBuffer(): MappedByteBuffer? = hyperplaneBuffer

    fun readSeqCounter(): Long {
        val buf = hyperplaneBuffer ?: return 0L
        return buf.getLong(OFFSET_SEQ)
    }

    fun readActiveBuffer(): Int {
        val buf = hyperplaneBuffer ?: return 0
        return buf.get(OFFSET_ACTIVE).toInt() and 0xFF
    }

    fun readKalmanState(): FloatArray {
        val buf = hyperplaneBuffer ?: return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            buf.getFloat(OFFSET_KALMAN),
            buf.getFloat(OFFSET_KALMAN + 4),
            buf.getFloat(OFFSET_KALMAN + 8)
        )
    }

    fun readBiquadCoefs(): Array<IntArray> {
        val buf = hyperplaneBuffer ?: return Array(64) { IntArray(5) }
        val result = Array(64) { IntArray(5) }
        for (i in 0 until 64) {
            for (j in 0 until 5) {
                result[i][j] = buf.getInt(OFFSET_BIQUAD + ((i * 5 + j) * 4))
            }
        }
        return result
    }

    fun readTemperatures(): ShortArray {
        val buf = hyperplaneBuffer ?: return ShortArray(10)
        val temps = ShortArray(10)
        for (i in 0 until 10) {
            temps[i] = buf.getShort(OFFSET_TEMP + (i * 2))
        }
        return temps
    }

    fun writeFusionLevel(level: Float) {
        hyperplaneBuffer?.putFloat(OFFSET_KALMAN + 12, level)
    }

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            nativeLibLoaded = true
            Log.i(TAG, "Librería nativa cargada correctamente")
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            lastError = "Native lib: ${e.message}"
            Log.e(TAG, "ERROR: No se pudo cargar librería nativa: ${e.message}")
        }
    }
}
