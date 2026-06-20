// ivanna_fusion.cpp — Motor principal: PEQ → Compressor → Exciter
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "ivanna_fusion.h"
#include <cstring>
#include <algorithm>

namespace ivanna {

IvannaFusionEngine::IvannaFusionEngine() noexcept {
    std::memset(buf_l_, 0, sizeof(buf_l_));
    std::memset(buf_r_, 0, sizeof(buf_r_));
}

bool IvannaFusionEngine::init(float samplerate, int channel_count) noexcept {
    samplerate_ = samplerate;
    channels_   = channel_count;

    peq_.setSamplerate(samplerate);
    comp_.setSamplerate(samplerate);
    exciter_.setSamplerate(samplerate);

    return true;
}

void IvannaFusionEngine::reset() noexcept {
    peq_.resetState();
    comp_.resetState();
    exciter_.resetState();
    std::memset(buf_l_, 0, sizeof(buf_l_));
    std::memset(buf_r_, 0, sizeof(buf_r_));
}

// ─── Hot path ─────────────────────────────────────────────────────────────────
void IvannaFusionEngine::process(const float* inL, const float* inR,
                                  float* outL, float* outR, int n_frames) noexcept {
    if (global_bypass_.load(std::memory_order_relaxed)) {
        if (inL != outL) std::memcpy(outL, inL, n_frames * sizeof(float));
        if (inR != outR) std::memcpy(outR, inR, n_frames * sizeof(float));
        return;
    }

    int n = std::min(n_frames, 4096);

    // Usar buffers internos para evitar aliasing si in == out
    const float* srcL = inL;
    const float* srcR = inR;

    // ── Etapa 1: Parametric EQ ────────────────────────────────────────────────
    peq_.process(srcL, srcR, buf_l_, buf_r_, n);

    // ── Etapa 2: Compresor (linked stereo) ───────────────────────────────────
    comp_.process(buf_l_, buf_r_, buf_l_, buf_r_, n);

    // ── Etapa 3: Excitador Armónico ──────────────────────────────────────────
    exciter_.process(buf_l_, buf_r_, outL, outR, n);
}

// ─── Control de parámetros ───────────────────────────────────────────────────
bool IvannaFusionEngine::setParam(int32_t id, float value) noexcept {
    // PEQ gains
    if (id >= PARAM_PEQ_GAIN_BASE && id < PARAM_PEQ_GAIN_BASE + dsp::PEQ_BANDS) {
        peq_.setBandGain(id - PARAM_PEQ_GAIN_BASE, value);
        return true;
    }
    // PEQ frequencies
    if (id >= PARAM_PEQ_FREQ_BASE && id < PARAM_PEQ_FREQ_BASE + dsp::PEQ_BANDS) {
        int b = id - PARAM_PEQ_FREQ_BASE;
        auto p = peq_.getBand(b);
        const_cast<dsp::PeqBandParams&>(p).frequency_hz = value;
        peq_.setBand(b, p);
        return true;
    }
    // PEQ Q
    if (id >= PARAM_PEQ_Q_BASE && id < PARAM_PEQ_Q_BASE + dsp::PEQ_BANDS) {
        int b = id - PARAM_PEQ_Q_BASE;
        auto p = peq_.getBand(b);
        const_cast<dsp::PeqBandParams&>(p).Q_or_slope = value;
        peq_.setBand(b, p);
        return true;
    }
    // PEQ bypass
    if (id == PARAM_PEQ_BYPASS) {
        peq_.setBypass(value != 0.f);
        return true;
    }
    // Compressor
    if (id == PARAM_COMP_THRESHOLD) { comp_.setThreshold(value); return true; }
    if (id == PARAM_COMP_RATIO)     { comp_.setRatio(value);     return true; }
    if (id == PARAM_COMP_KNEE)      { comp_.setKnee(value);      return true; }
    if (id == PARAM_COMP_ATTACK)    { comp_.setAttack(value);    return true; }
    if (id == PARAM_COMP_RELEASE)   { comp_.setRelease(value);   return true; }
    if (id == PARAM_COMP_MAKEUP)    { comp_.setMakeup(value);    return true; }
    if (id == PARAM_COMP_BYPASS)    { comp_.setBypass(value != 0.f); return true; }
    // Exciter
    if (id == PARAM_EXC_DRIVE)     { exciter_.setDrive(value);    return true; }
    if (id == PARAM_EXC_MIX)       { exciter_.setMix(value);      return true; }
    if (id == PARAM_EXC_HPF_FREQ)  { exciter_.setHpfFreq(value);  return true; }
    if (id == PARAM_EXC_BYPASS)    { exciter_.setBypass(value != 0.f); return true; }
    // Global
    if (id == PARAM_GLOBAL_BYPASS) { setGlobalBypass(value != 0.f); return true; }
    // Preset
    if (id == PARAM_PRESET_LOAD)   { loadPreset((int)value); return true; }

    return false;
}

bool IvannaFusionEngine::getParam(int32_t id, float& out) noexcept {
    if (id >= PARAM_PEQ_GAIN_BASE && id < PARAM_PEQ_GAIN_BASE + dsp::PEQ_BANDS) {
        out = peq_.getBand(id - PARAM_PEQ_GAIN_BASE).gain_dB;
        return true;
    }
    if (id == PARAM_GET_GAIN_DB) {
        out = comp_.currentGainDB();
        return true;
    }
    if (id == PARAM_GLOBAL_BYPASS) {
        out = global_bypass_.load() ? 1.f : 0.f;
        return true;
    }
    return false;
}

void IvannaFusionEngine::loadPreset(int preset_id) noexcept {
    using namespace dsp;
    switch (preset_id) {
        case 0: {
            auto params = peq_default_params();
            for (int b = 0; b < PEQ_BANDS; ++b) peq_.setBand(b, params[b]);
            comp_.setThreshold(-18.f); comp_.setRatio(2.f); comp_.setMakeup(1.f);
            exciter_.setDrive(2.f);    exciter_.setMix(0.15f);
            break;
        }
        case 1: {
            // Preset Rock Clásico — calibrado para Rush, Grand Funk Railroad
            auto params = peq_preset_classic_rock();
            for (int b = 0; b < PEQ_BANDS; ++b) peq_.setBand(b, params[b]);
            comp_.setThreshold(-20.f); comp_.setRatio(3.f); comp_.setMakeup(2.f);
            comp_.setAttack(5.f); comp_.setRelease(80.f);
            exciter_.setDrive(3.5f);   exciter_.setMix(0.25f);
            exciter_.setHpfFreq(2500.f);
            break;
        }
        default: break;
    }
    // NOTA: reset() NO se llama aquí. Llamarlo después de setBand/setParam
    // borraría los estados internos de los filtros biquad, el RMS buffer del
    // compresor y el envelope follower justo cuando ya tienen los nuevos
    // coeficientes, produciendo un clic audible y un período de 'warm-up'
    // innecesario. El caller debe llamar reset() ANTES del cambio de preset
    // si necesita un arranque limpio (silencio previo garantizado).
}

} // namespace ivanna
