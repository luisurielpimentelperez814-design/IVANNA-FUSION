package com.ivannafusion

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileReader

object ThermalMonitor {
    private const val TAG = "IVANNA-Thermal"

    var temp_cpu_core0: Short = 0
    var temp_cpu_core1: Short = 0
    var temp_cpu_core2: Short = 0
    var temp_cpu_core3: Short = 0
    var temp_cpu_core4: Short = 0
    var temp_cpu_core5: Short = 0
    var temp_cpu_core6: Short = 0
    var temp_cpu_core7: Short = 0
    var temp_gpu: Short = 0
    var temp_npu: Short = 0
    var temp_pmic: Short = 0

    var sched_nucleo_activo: Short = 4
    var sched_budget_mW: Short = 1500
    var sched_throttle_predicho: Boolean = false

    private var monitoringThread: Thread? = null
    @Volatile private var isMonitoring = false

    fun initialize(context: Context) {
        if (isMonitoring) return
        loadEBPFProgram()
        startMonitoring()
    }

    private fun loadEBPFProgram() {
        try {
            val bpfPath = "/system/etc/ivanna/thermal_sched.bpf.o"
            val bpfFile = File(bpfPath)
            if (bpfFile.exists()) {
                Runtime.getRuntime()
                    .exec("su -c \"bpftool prog load $bpfPath /sys/fs/bpf/ivanna_thermal\"")
                    .waitFor()
            }
        } catch (e: Exception) {
            Log.d(TAG, "eBPF térmico no disponible: ${e.message}")
        }
    }

    private fun startMonitoring() {
        isMonitoring = true
        monitoringThread = Thread {
            while (isMonitoring) {
                try {
                    readThermalZones()
                    updateHyperplane()
                    updateSchedulerHints()
                    Thread.sleep(250)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Tick térmico falló", e)
                }
            }
        }.apply {
            name = "IVANNA-Thermal"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun readThermalZones() {
        val thermalPaths = mapOf(
            "cpu0" to "/sys/class/thermal/thermal_zone0/temp",
            "cpu1" to "/sys/class/thermal/thermal_zone1/temp",
            "gpu" to "/sys/class/thermal/thermal_zone2/temp",
            "npu" to "/sys/class/thermal/thermal_zone3/temp",
            "pmic" to "/sys/class/thermal/thermal_zone4/temp"
        )

        thermalPaths.forEach { (name, path) ->
            try {
                val temp = FileReader(path).use { it.readText().trim().toInt() / 1000 }.toShort()
                when (name) {
                    "cpu0" -> temp_cpu_core0 = temp
                    "cpu1" -> temp_cpu_core1 = temp
                    "gpu" -> temp_gpu = temp
                    "npu" -> temp_npu = temp
                    "pmic" -> temp_pmic = temp
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun updateHyperplane() {
        val temps = shortArrayOf(
            temp_cpu_core0, temp_cpu_core1, temp_cpu_core2, temp_cpu_core3,
            temp_cpu_core4, temp_cpu_core5, temp_cpu_core6, temp_cpu_core7,
            temp_gpu, temp_npu
        )
        ShmManager.writeTemperatures(temps)
    }

    private fun updateSchedulerHints() {
        val maxTemp = getMaxTemperature().toInt()
        sched_throttle_predicho = maxTemp >= 42
        sched_nucleo_activo = when {
            maxTemp >= 48 -> 2
            maxTemp >= 44 -> 4
            else -> 6
        }
        sched_budget_mW = when {
            maxTemp >= 48 -> 900
            maxTemp >= 44 -> 1200
            else -> 1600
        }
    }

    fun getMaxTemperature(): Short {
        return arrayOf(
            temp_cpu_core0, temp_cpu_core1, temp_cpu_core2, temp_cpu_core3,
            temp_cpu_core4, temp_cpu_core5, temp_cpu_core6, temp_cpu_core7,
            temp_gpu, temp_npu, temp_pmic
        ).maxOrNull() ?: 0
    }

    fun shutdown() {
        isMonitoring = false
        monitoringThread?.interrupt()
        try {
            monitoringThread?.join(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        monitoringThread = null
    }
}
