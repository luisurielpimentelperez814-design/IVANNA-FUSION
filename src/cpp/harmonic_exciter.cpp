// harmonic_exciter.cpp — Excitador armónico via waveshaping tanh + HPF
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "harmonic_exciter.h"
#include <cstring>
#include <cmath>
#include <algorithm>

namespace ivanna::dsp {

HarmonicExciter::HarmonicExciter() noexcept {
    // Los filtros se inicializan en flat hasta que setSamplerate sea llamado
    hpf_pre_coeffs_  = make_flat();
    hpf_post_coeffs_ = make_flat();
}

void HarmonicExciter::setSamplerate(float fs) noexcept {
    samplerate_ = fs;
    setHpfFreq(hpf_freq_);
    // Recalcular norma de tanh con el drive actual
    float drive = drive_.load(std::memory_order_relaxed);
    float d_clamped = std::max(drive, 0.01f);
    tanh_norm_inv_ = 1.f / std::tanh(d_clamped);
}

void HarmonicExciter::setHpfFreq(float hz) noexcept {
    hpf_freq_ = hz;
    if (samplerate_ < 1.f) return;

    // HPF pre-waveshaper: Q=0.707 (Butterworth)
    hpf_pre_coeffs_  = make_highpass(hz, 0.707f, samplerate_);
    // HPF post-waveshaper: frecuencia ligeramente más alta para enfatizar presencia
    hpf_post_coeffs_ = make_highpass(hz * 1.5f, 0.707f, samplerate_);
}

void HarmonicExciter::resetState() noexcept {
    hpf_pre_state_.reset();
    hpf_post_state_.reset();
}

// ─── Hot path ─────────────────────────────────────────────────────────────────
void HarmonicExciter::process(const float* inL, const float* inR,
                               float* outL, float* outR, int n_frames) noexcept {
    if (bypass_.load(std::memory_order_relaxed)) {
        if (inL != outL) std::memcpy(outL, inL, n_frames * sizeof(float));
        if (inR != outR) std::memcpy(outR, inR, n_frames * sizeof(float));
        return;
    }

    float drive  = drive_.load(std::memory_order_relaxed);
    float mix    = mix_.load(std::memory_order_relaxed);

    // Actualizar norma si el drive cambió
    float d_clamped = std::max(drive, 0.01f);
    tanh_norm_inv_ = 1.f / std::tanh(d_clamped);

    // Buffers temporales de armónicos (stack-allocated, seguro para frames cortos)
    // AudioFlinger usa frames de 240-480 samples típicamente
    // Para frames más largos usamos buffer estático (no concurrent — single thread)
    static float hL[4096];
    static float hR[4096];

    int n = std::min(n_frames, 4096);

    // ── PASO 1: HPF sobre la señal original ──────────────────────────────────
    // Elimina sub-bajos para no distorsionar frecuencias fundamentales del bajo
    biquad_process_block_stereo(hpf_pre_coeffs_, hpf_pre_state_,
                                 inL, inR, hL, hR, n);

    // ── PASO 2: Waveshaping tanh (genera armónicos) ───────────────────────────
    // ysat = tanh(drive * x) / tanh(drive)
    // → normalizado: ganancia unitaria en señal pequeña
    for (int i = 0; i < n; ++i) {
        hL[i] = std::tanh(drive * hL[i]) * tanh_norm_inv_;
        hR[i] = std::tanh(drive * hR[i]) * tanh_norm_inv_;
    }

    // ── PASO 3: HPF post-waveshaper ───────────────────────────────────────────
    // Extrae solo los armónicos NUEVOS (la fundamental ya estaba en la entrada)
    // harm = HPF(sat) - HPF(x) ≈ solo los harmónicos generados
    biquad_process_block_stereo(hpf_post_coeffs_, hpf_post_state_,
                                 hL, hR, hL, hR, n);

    // ── PASO 4: Mezcla de armónicos en la señal original ─────────────────────
    for (int i = 0; i < n; ++i) {
        outL[i] = inL[i] + mix * hL[i];
        outR[i] = inR[i] + mix * hR[i];
    }
}

} // namespace ivanna::dsp
