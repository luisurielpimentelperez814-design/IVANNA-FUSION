package com.ivannafusion

// 🔧 AUTO-GENERATED STUBS (NO DELETE MODE)

object AudioStubs {

    fun AudioStubs.feedExternalMonoAudio(data: FloatArray) {}

    fun AudioStubs.aiGetConfidence(): Float = 0f
    fun aiGetCentroidHz(): Float = 0f
    fun aiGetBassEnergy(): Float = 0f
    fun aiGetMidEnergy(): Float = 0f
    fun aiGetHighEnergy(): Float = 0f
    fun aiGetZcr(): Float = 0f

    fun aiSetEnabled(v: Boolean) {}
    fun aiSetAutoAdapt(v: Boolean) {}
    fun aiSetSensitivity(v: Float) {}

    fun AudioStubs.eqSetBypass(v: Boolean) {}
    fun eqSetGain(v: Float) {}

    fun AudioStubs.compSetBypass(v: Boolean) {}
    fun AudioStubs.compSetThreshold(v: Float) {}
    fun AudioStubs.compSetRatio(v: Float) {}
    fun AudioStubs.compSetAttack(v: Float) {}
    fun AudioStubs.compSetRelease(v: Float) {}
    fun AudioStubs.compSetKnee(v: Float) {}
    fun AudioStubs.compSetMakeup(v: Float) {}

    fun convSetType(v: Int) {}
    fun convSetDecay(v: Float) {}
    fun convSetPreDelay(v: Float) {}
    fun convSetDamping(v: Float) {}
    fun convSetDiffusion(v: Float) {}
    fun convSetMix(v: Float) {}

    fun decorSetWidth(v: Float) {}
    fun decorSetDepth(v: Float) {}
    fun decorSetMix(v: Float) {}

    fun pfEvoTick() {}
    fun pfEvoReset() {}
    fun applyPFPreset(id: Int) {}

    fun setPreset(id: Int) {}
}
