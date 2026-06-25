package com.ivannafusion

import kotlinx.coroutines.*

class MuController {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isUpdating = false
    private var currentMu = 500  // valor inicial

    fun startUpdating() {
        if (isUpdating) return
        isUpdating = true
        scope.launch {
            while (isUpdating) {
                // Simular errores (en producción, calcular de la señal)
                val spatialErr = 10
                val roomErr = 5
                val maskingErr = 2
                currentMu = IvannaNativeLib.nativeUpdateMu(spatialErr, roomErr, maskingErr)
                // Actualizar el estado global legacy (para que la UI lo muestre)
                DSPState.mu = currentMu  // Nota: DSPState es un object con var
                delay(50)  // Actualizar cada 50 ms
            }
        }
    }

    fun stopUpdating() {
        isUpdating = false
        scope.cancel()
    }

    fun getCurrentMu(): Int = currentMu
}
