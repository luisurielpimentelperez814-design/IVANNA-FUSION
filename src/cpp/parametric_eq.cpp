// parametric_eq.cpp — Ecualizador Paramétrico 8 bandas
// Cascada de biquads IIR con NEON estéreo
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "parametric_eq.h"
#include <cstring>
#include <algorithm>

namespace ivanna::dsp {

ParametricEQ::ParametricEQ() noexcept {
    params_ = peq_default_params();
    for (int i = 0; i < PEQ_BANDS; ++i) coeffs_[i] = make_flat();
    for (int i = 0; i < PEQ_BANDS; ++i) states_[i].reset();
}

void ParametricEQ::setSamplerate(float fs) noexcept {
    samplerate_ = fs;
    recalcAll();
}

void ParametricEQ::setBand(int band, const PeqBandParams& params) noexcept {
    if (band < 0 || band >= PEQ_BANDS) return;
    params_[band] = params;
    recalcBand(band);
}

void ParametricEQ::setBandGain(int band, float gain_dB) noexcept {
    if (band < 0 || band >= PEQ_BANDS) return;
    params_[band].gain_dB = gain_dB;
    recalcBand(band);
}

void ParametricEQ::resetState() noexcept {
    for (auto& s : states_) s.reset();
}

void ParametricEQ::recalcBand(int i) noexcept {
    const auto& p = params_[i];

    if (!p.enabled || std::fabs(p.gain_dB) < 0.01f) {
        // Ganancia cero → pass-through para evitar ruido de redondeo
        coeffs_[i] = make_flat();
        return;
    }

    switch (p.type) {
        case BandType::PEAKING:
            coeffs_[i] = make_peaking(p.frequency_hz, p.Q_or_slope, p.gain_dB, samplerate_);
            break;
        case BandType::LOW_SHELF:
            coeffs_[i] = make_low_shelf(p.frequency_hz, p.Q_or_slope, p.gain_dB, samplerate_);
            break;
        case BandType::HIGH_SHELF:
            coeffs_[i] = make_high_shelf(p.frequency_hz, p.Q_or_slope, p.gain_dB, samplerate_);
            break;
        case BandType::LOWPASS:
            coeffs_[i] = make_lowpass(p.frequency_hz, p.Q_or_slope, samplerate_);
            break;
        case BandType::HIGHPASS:
            coeffs_[i] = make_highpass(p.frequency_hz, p.Q_or_slope, samplerate_);
            break;
    }
}

void ParametricEQ::recalcAll() noexcept {
    for (int i = 0; i < PEQ_BANDS; ++i) recalcBand(i);
}

// ─── Hot path ─────────────────────────────────────────────────────────────────
void ParametricEQ::process(const float* inL, const float* inR,
                            float* outL, float* outR, int n_frames) noexcept {
    if (bypass_.load(std::memory_order_relaxed)) {
        if (inL != outL) std::memcpy(outL, inL, n_frames * sizeof(float));
        if (inR != outR) std::memcpy(outR, inR, n_frames * sizeof(float));
        return;
    }

    // Primera banda: procesar desde los buffers de entrada
    biquad_process_block_stereo(coeffs_[0], states_[0],
                                 inL, inR, outL, outR, n_frames);

    // Bandas restantes: cascada in-place sobre outL/outR
    for (int b = 1; b < PEQ_BANDS; ++b) {
        biquad_process_block_stereo(coeffs_[b], states_[b],
                                     outL, outR, outL, outR, n_frames);
    }
}

} // namespace ivanna::dsp
