/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.app.Application

class IVANNAApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicialización global del sistema trascendental
        ShmManager.initialize(this)
        AudioEngine.initialize(this)
        ThermalMonitor.initialize(this)
    }
}
