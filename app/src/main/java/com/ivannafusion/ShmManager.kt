package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()

    fun initialize(context: Context) {
        _shmStatus.value = "SHM inicializada"
        // Aquí puedes llamar a createSharedMemory si es necesario
    }

    fun createSharedMemory(name: String, size: Int): SharedMemory? {
        return try {
            val shm = SharedMemory.create(name, size)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            _shmStatus.value = "SHM creada correctamente"
            shm
        } catch (e: Exception) {
            _shmStatus.value = "Error SHM: ${e.message}"
            null
        }
    }

    fun close() {
        _shmStatus.value = "SHM cerrada"
        // liberar recursos nativos
    }
}
