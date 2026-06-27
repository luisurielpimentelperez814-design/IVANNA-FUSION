package com.ivanna.fusion.pro

import android.util.Log

/**
 * Bridge singleton hacia libivanna_dsp.so
 * Los 5 JNI están en ivanna_dsp.cpp (pro_dsp/).
 * nativeProcess ya NO hace new[]/delete[] — usa scratch pre-alocado en el contexto C++.
 */
object DSPBridge {

    private const val TAG = "IVANNA_DSP"
    private var loaded = false

    init {
        try {
            System.loadLibrary("ivanna_dsp")
            loaded = true
            Log.i(TAG, "libivanna_dsp loaded — ${nativeVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native lib not available: ${e.message}")
        }
    }

    fun init(sampleRate: Int = 48000) {
        if (loaded) nativeInit(sampleRate)
    }

    fun setParams(
        drive: Float, wet: Float, mix: Float,
        alpha: Float, beta: Float, gamma: Float,
        freq: Float, resonance: Float,
        low: Float, mid: Float, high: Float,
        presence: Float, master: Float
    ) {
        if (!loaded) return
        nativeSetParams(drive, wet, mix, alpha, beta, gamma,
                        freq, resonance, low, mid, high, presence, master)
    }

    fun process(buffer: FloatArray, numFrames: Int) {
        if (loaded) nativeProcess(buffer, numFrames)
    }

    fun reset() {
        if (loaded) nativeReset()
    }

    fun version(): String = if (loaded) nativeVersion() else "native unavailable"

    private external fun nativeInit(sampleRate: Int)
    private external fun nativeProcess(buf: FloatArray, numFrames: Int)
    private external fun nativeReset()
    private external fun nativeSetParams(
        drive: Float, wet: Float, mix: Float,
        alpha: Float, beta: Float, gamma: Float,
        freq: Float, resonance: Float,
        low: Float, mid: Float, high: Float,
        presence: Float, master: Float
    )
    private external fun nativeVersion(): String
}
