package com.ivannafusion

import android.media.audiofx.AudioEffect
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object DSPController {
    private const val TAG = "DSPController"
    
    private val TYPE_IVANNA = UUID.fromString("ec7178a0-847d-11e0-a3cb-0002a5d5c51b")
    private val UUID_IVANNA = UUID.fromString("7b3be4ec-c23c-4e6e-8c6d-49e5f4d54ea3")

    private var effect: AudioEffect? = null

    fun init() {
        if (effect != null) return
        // CRÍTICO: crear un AudioEffect con audioSession=0 (sesión global del
        // mix maestro) vía reflexión sobre un constructor no público es una
        // operación privilegiada que en algunos frameworks de audio de
        // fabricante puede bloquear el hilo de binder esperando una
        // respuesta de audioserver que nunca llega (especialmente si
        // audioserver está en un estado inestable). Se ejecuta con un
        // timeout duro en un hilo separado para que, si se cuelga, no
        // arrastre al hilo que llamó a init() ni, por extensión, al
        // sistema completo — solo falla esta función puntual.
        val initThread = Thread {
            try {
                val constructor = AudioEffect::class.java.getDeclaredConstructor(
                    UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
                constructor.isAccessible = true
                val created = constructor.newInstance(TYPE_IVANNA, UUID_IVANNA, 0, 0) as AudioEffect
                created.enabled = true
                effect = created
                Log.i(TAG, "DSPController inicializado con AudioEffect exitoso")
            } catch (e: Exception) {
                Log.e(TAG, "Error inicializando AudioEffect", e)
            }
        }
        initThread.isDaemon = true  // si se cuelga, no impide que la JVM/proceso termine
        initThread.start()
        initThread.join(2000)  // máximo 2s de espera; si no termina, seguimos sin AudioEffect
        if (initThread.isAlive) {
            Log.e(TAG, "DSPController.init() no respondió en 2s — continuando SIN efecto global " +
                       "(posible binder colgado hacia audioserver; no se bloquea el resto de la app)")
        }
    }

    fun release() {
        try {
            effect?.release()
            effect = null
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando AudioEffect", e)
        }
    }

    private fun setParam(paramId: Int, value: Float) {
        val eff = effect ?: return
        val paramBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
        val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
        try {
            // Usamos Reflexión porque setParameter(byte[], byte[]) es una API oculta (@hide) en Android
            val method = AudioEffect::class.java.getDeclaredMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
            method.isAccessible = true
            method.invoke(eff, paramBytes, valueBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error seteando param $paramId", e)
        }
    }
    
    private fun setParam(paramId: Int, value: Int) {
        val eff = effect ?: return
        val paramBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
        val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(value).array()
        try {
            // Usamos Reflexión para consistencia con el método de bytes
            val method = AudioEffect::class.java.getDeclaredMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
            method.isAccessible = true
            method.invoke(eff, paramBytes, valueBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error seteando param int $paramId", e)
        }
    }

    // PEQ
    fun eqSetGain(band: Int, gain: Float) { if (band < 8) setParam(0x00 + band, gain) }
    fun eqSetFreq(band: Int, freq: Float) { if (band < 8) setParam(0x10 + band, freq) }
    fun eqSetQ(band: Int, q: Float) { if (band < 8) setParam(0x20 + band, q) }
    fun eqSetEnabled(band: Int, enabled: Boolean) { if (band < 8) setParam(0x30 + band, if(enabled) 1 else 0) }
    fun eqSetBypass(bypass: Boolean) = setParam(0x40, if(bypass) 1 else 0)

    // Compressor
    fun compSetThreshold(thr: Float) = setParam(0x50, thr)
    fun compSetRatio(ratio: Float) = setParam(0x51, ratio)
    fun compSetKnee(knee: Float) = setParam(0x52, knee)
    fun compSetAttack(attack: Float) = setParam(0x53, attack)
    fun compSetRelease(release: Float) = setParam(0x54, release)
    fun compSetMakeup(makeup: Float) = setParam(0x55, makeup)
    fun compSetBypass(bypass: Boolean) = setParam(0x56, if(bypass) 1 else 0)

    // Exciter
    fun excSetDrive(drive: Float) = setParam(0x60, drive)
    fun excSetMix(mix: Float) = setParam(0x61, mix)
    fun excSetHpfFreq(freq: Float) = setParam(0x62, freq)
    fun excSetBypass(bypass: Boolean) = setParam(0x63, if(bypass) 1 else 0)

    // Global
    fun setGlobalBypass(bypass: Boolean) = setParam(0x70, if(bypass) 1 else 0)
    fun loadPreset(id: Int) = setParam(0x72, id)
}
