package com.ivannafusion

object IvannaNativeLib {
    init {
        try {
            System.loadLibrary("ivanna_trascendental")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("IvannaNativeLib", "Native lib not available: ${e.message}")
        }
    }

    // Basic
    @JvmStatic external fun nativeSetEnabled(enabled: Boolean)
    @JvmStatic external fun nativeIsEnabled(): Boolean
    @JvmStatic external fun nativeSetPreset(id: Int)
    @JvmStatic external fun nativeReset()

    // EQ
    @JvmStatic external fun nativeEqSetGain(b: Int, g: Float)
    @JvmStatic external fun nativeEqGetGain(b: Int): Float
    @JvmStatic external fun nativeEqSetThreshold(b: Int, t: Float)
    @JvmStatic external fun nativeEqSetRatio(b: Int, r: Float)
    @JvmStatic external fun nativeEqGetGainReduction(b: Int): Float

    // Compressor
    @JvmStatic external fun nativeCompSetThreshold(b: Int, t: Float)
    @JvmStatic external fun nativeCompSetRatio(b: Int, r: Float)
    @JvmStatic external fun nativeCompSetAttack(b: Int, a: Float)
    @JvmStatic external fun nativeCompSetRelease(b: Int, r: Float)

    // Surround
    @JvmStatic external fun nativeSurroundSetWidth(w: Float)
    @JvmStatic external fun nativeSurroundSetLevel(l: Float)
    @JvmStatic external fun nativeSurroundSetHeight(h: Float)
    @JvmStatic external fun nativeSurroundSetRoom(r: Float)

    // Widener
    @JvmStatic external fun nativeWidenerSetWidth(w: Float)

    // Bass
    @JvmStatic external fun nativeBassSetAmount(a: Float)
    @JvmStatic external fun nativeBassSetFrequency(f: Float)

    // Upscaler
    @JvmStatic external fun nativeUpscalerSetAmount(a: Float)
    @JvmStatic external fun nativeUpscalerSetCeiling(c: Float)

    // Loudness
    @JvmStatic external fun nativeGetMomentaryLoudness(): Float
    @JvmStatic external fun nativeGetShortTermLoudness(): Float
    @JvmStatic external fun nativeGetIntegratedLoudness(): Float
    @JvmStatic external fun nativeGetPeakLevel(): Float
    @JvmStatic external fun nativeGetCorrelation(): Float
    @JvmStatic external fun nativeGetLoudnessRange(): Float

    // Convolver
    @JvmStatic external fun nativeConvolverSetEnabled(e: Boolean)
    @JvmStatic external fun nativeConvolverLoadPreset(id: Int)
    @JvmStatic external fun nativeConvolverSetMix(m: Float)

    // AutoEQ
    @JvmStatic external fun nativeAutoeqApply(name: String)

    // AI
    @JvmStatic external fun nativeAiSetEnabled(e: Boolean)
    @JvmStatic external fun nativeAiSetAutoAdapt(a: Boolean)
    @JvmStatic external fun nativeAiSetSensitivity(s: Float)
    @JvmStatic external fun nativeAiGetDetectedGenre(): String
    @JvmStatic external fun nativeAiGetConfidence(): Float
    @JvmStatic external fun nativeAiGetTempo(): Float
    @JvmStatic external fun nativeAiGetCurrentCurveName(): String
    @JvmStatic external fun nativeAiGetCurrentCurveDescription(): String
    @JvmStatic external fun nativeAiApplyCurrentCurve()
    @JvmStatic external fun nativeAiSaveAsPreset(name: String)

    // Safe wrappers
    fun setEnabled(v: Boolean) = try { nativeSetEnabled(v) } catch (e: Exception) {}
    fun isEnabled(): Boolean = try { nativeIsEnabled() } catch (e: Exception) { false }
    fun setPreset(p: Int) = try { nativeSetPreset(p) } catch (e: Exception) {}
    fun reset() = try { nativeReset() } catch (e: Exception) {}

    fun eqSetGain(b: Int, g: Float) = try { nativeEqSetGain(b, g) } catch (e: Exception) {}
    fun eqGetGain(b: Int): Float = try { nativeEqGetGain(b) } catch (e: Exception) { 0f }
    fun eqSetThreshold(b: Int, t: Float) = try { nativeEqSetThreshold(b, t) } catch (e: Exception) {}
    fun eqSetRatio(b: Int, r: Float) = try { nativeEqSetRatio(b, r) } catch (e: Exception) {}
    fun eqGetGainReduction(b: Int): Float = try { nativeEqGetGainReduction(b) } catch (e: Exception) { 0f }

    fun compSetThreshold(b: Int, t: Float) = try { nativeCompSetThreshold(b, t) } catch (e: Exception) {}
    fun compSetRatio(b: Int, r: Float) = try { nativeCompSetRatio(b, r) } catch (e: Exception) {}
    fun compSetAttack(b: Int, a: Float) = try { nativeCompSetAttack(b, a) } catch (e: Exception) {}
    fun compSetRelease(b: Int, r: Float) = try { nativeCompSetRelease(b, r) } catch (e: Exception) {}

    fun surroundSetWidth(w: Float) = try { nativeSurroundSetWidth(w) } catch (e: Exception) {}
    fun surroundSetLevel(l: Float) = try { nativeSurroundSetLevel(l) } catch (e: Exception) {}
    fun surroundSetHeight(h: Float) = try { nativeSurroundSetHeight(h) } catch (e: Exception) {}
    fun surroundSetRoom(r: Float) = try { nativeSurroundSetRoom(r) } catch (e: Exception) {}

    fun widenerSetWidth(w: Float) = try { nativeWidenerSetWidth(w) } catch (e: Exception) {}
    fun bassSetAmount(a: Float) = try { nativeBassSetAmount(a) } catch (e: Exception) {}
    fun bassSetFrequency(f: Float) = try { nativeBassSetFrequency(f) } catch (e: Exception) {}
    fun upscalerSetAmount(a: Float) = try { nativeUpscalerSetAmount(a) } catch (e: Exception) {}
    fun upscalerSetCeiling(c: Float) = try { nativeUpscalerSetCeiling(c) } catch (e: Exception) {}

    fun getMomentaryLoudness(): Float = try { nativeGetMomentaryLoudness() } catch (e: Exception) { -70f }
    fun getShortTermLoudness(): Float = try { nativeGetShortTermLoudness() } catch (e: Exception) { -70f }
    fun getIntegratedLoudness(): Float = try { nativeGetIntegratedLoudness() } catch (e: Exception) { -70f }
    fun getPeakLevel(): Float = try { nativeGetPeakLevel() } catch (e: Exception) { -100f }
    fun getCorrelation(): Float = try { nativeGetCorrelation() } catch (e: Exception) { 0f }
    fun getLoudnessRange(): Float = try { nativeGetLoudnessRange() } catch (e: Exception) { 0f }

    fun convolverSetEnabled(e: Boolean) = try { nativeConvolverSetEnabled(e) } catch (ex: Exception) {}
    fun convolverLoadPreset(id: Int) = try { nativeConvolverLoadPreset(id) } catch (e: Exception) {}
    fun convolverSetMix(m: Float) = try { nativeConvolverSetMix(m) } catch (e: Exception) {}

    fun autoeqApply(name: String) = try { nativeAutoeqApply(name) } catch (e: Exception) {}
    fun aiSetEnabled(e: Boolean) = try { nativeAiSetEnabled(e) } catch (ex: Exception) {}
    fun aiSetAutoAdapt(a: Boolean) = try { nativeAiSetAutoAdapt(a) } catch (e: Exception) {}
    fun aiSetSensitivity(s: Float) = try { nativeAiSetSensitivity(s) } catch (e: Exception) {}
    fun aiGetDetectedGenre(): String = try { nativeAiGetDetectedGenre() } catch (e: Exception) { "unknown" }
    fun aiGetConfidence(): Float = try { nativeAiGetConfidence() } catch (e: Exception) { 0f }
    fun aiGetTempo(): Float = try { nativeAiGetTempo() } catch (e: Exception) { 0f }
    fun aiGetCurrentCurveName(): String = try { nativeAiGetCurrentCurveName() } catch (e: Exception) { "N/A" }
    fun aiGetCurrentCurveDescription(): String = try { nativeAiGetCurrentCurveDescription() } catch (e: Exception) { "" }
    fun aiApplyCurrentCurve() = try { nativeAiApplyCurrentCurve() } catch (e: Exception) {}
    fun aiSaveAsPreset(n: String) = try { nativeAiSaveAsPreset(n) } catch (e: Exception) {}
}
