package com.ivannafusion

import kotlinx.coroutines.*

class MuController {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isRunning) {
                val spatialErr = (0..20).random()
                val roomErr = (0..10).random()
                val maskingErr = (0..5).random()
                val newMu = IvannaNativeLib.nativeUpdateMu(spatialErr, roomErr, maskingErr)
                withContext(Dispatchers.Main) {
                    DSPState.mu = newMu
                }
                delay(100)
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }
}
