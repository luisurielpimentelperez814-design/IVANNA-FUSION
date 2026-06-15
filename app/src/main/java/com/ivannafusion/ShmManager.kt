package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

private const val TAG = "IVANNA-SHM"

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()

    private var hyperplaneBuffer: ByteBuffer? = null
    var nativeLibLoaded = false
    var shmInitialized = false

    fun initialize(context: Context) {
        _shmStatus.value = "Creando SharedMemory..."
        try {
            val shm = SharedMemory.create("ivanna_hyperplane", 2 * 1024 * 1024)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            hyperplaneBuffer = shm.mapReadWrite()
            _shmStatus.value = "SHM real activa"
            Log.i(TAG, "SharedMemory creada y mapeada correctamente")
        } catch (e: Exception) {
            _shmStatus.value = "Error SharedMemory: ${e.message}"
            Log.e(TAG, "Fallo SharedMemory, usando buffer directo: ${e.message}")
            hyperplaneBuffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            _shmStatus.value = "Usando buffer directo (fallback)"
        }
        shmInitialized = true
    }

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer
    fun close() { _shmStatus.value = "SHM cerrada" }

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            nativeLibLoaded = true
            _shmStatus.value = "Librería nativa cargada"
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            _shmStatus.value = "Error librería nativa"
        }
    }
}
