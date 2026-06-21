#pragma once
// biquad_neon.h — Filtro IIR Biquad con ARM NEON SIMD
// Direct Form II Transposed — estable numéricamente para shelving a frecuencias bajas
// Procesa canal L+R como par float32x2_t: 2x throughput vs. escalar
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <cstdint>
#include <cmath>
#include <array>

#ifdef __aarch64__
#include <arm_neon.h>
#define IVANNA_NEON 1
#endif

namespace ivanna::dsp {

// ─── Coeficientes normalizados (a0 = 1.0 siempre) ────────────────────────────
struct BiquadCoeffs {
    float b0, b1, b2;   // numerador
    float a1, a2;       // denominador (negado: y = b*x - a*state)
};

// ─── Estado por canal (Direct Form II Transposed) ─────────────────────────────
struct BiquadState {
    float w1 = 0.f;     // registro de estado 1
    float w2 = 0.f;     // registro de estado 2

    inline void reset() noexcept { w1 = w2 = 0.f; }
};

// ─── Estado estéreo empaquetado para NEON ─────────────────────────────────────
struct BiquadStateStereo {
    // [w1L, w1R] y [w2L, w2R] como pares NEON
    float w1[2] = {0.f, 0.f};  // índice 0 = L, 1 = R
    float w2[2] = {0.f, 0.f};

    inline void reset() noexcept {
        w1[0] = w1[1] = w2[0] = w2[1] = 0.f;
    }
};

// ─────────────────────────────────────────────────────────────────────────────
// Proceso escalar (fallback no-NEON / mono)
// ─────────────────────────────────────────────────────────────────────────────
inline float biquad_process_sample(const BiquadCoeffs& c, BiquadState& s, float x) noexcept {
    float y = c.b0 * x + s.w1;
    s.w1     = c.b1 * x - c.a1 * y + s.w2;
    s.w2     = c.b2 * x - c.a2 * y;
    return y;
}

// Procesa N muestras mono
void biquad_process_block_mono(const BiquadCoeffs& c, BiquadState& s,
                                const float* in, float* out, int n) noexcept;

// ─────────────────────────────────────────────────────────────────────────────
// Proceso estéreo con NEON (procesa L y R simultáneamente)
// Signature: arrays separados inL/inR → outL/outR
// ─────────────────────────────────────────────────────────────────────────────
void biquad_process_block_stereo(const BiquadCoeffs& c, BiquadStateStereo& s,
                                  const float* inL, const float* inR,
                                  float* outL, float* outR, int n) noexcept;

// ─────────────────────────────────────────────────────────────────────────────
// Generadores de coeficientes (Audio EQ Cookbook — R.A. Bristow-Johnson)
// Todas las frecuencias en Hz, Fs en Hz
// ─────────────────────────────────────────────────────────────────────────────

// Peaking EQ: ganancia dBgain en frequency_hz con Q factor
BiquadCoeffs make_peaking(float frequency_hz, float Q, float dBgain, float samplerate) noexcept;

// Low Shelf: ganancia dBgain por debajo de frequency_hz
BiquadCoeffs make_low_shelf(float frequency_hz, float shelf_slope, float dBgain, float samplerate) noexcept;

// High Shelf: ganancia dBgain por encima de frequency_hz
BiquadCoeffs make_high_shelf(float frequency_hz, float shelf_slope, float dBgain, float samplerate) noexcept;

// Low-pass Butterworth 2do orden
BiquadCoeffs make_lowpass(float cutoff_hz, float Q, float samplerate) noexcept;

// High-pass Butterworth 2do orden
BiquadCoeffs make_highpass(float cutoff_hz, float Q, float samplerate) noexcept;

// All-pass 2do orden (delay fraccional)
BiquadCoeffs make_allpass(float frequency_hz, float Q, float samplerate) noexcept;

// Flat (pass-through): b0=1, resto=0
inline BiquadCoeffs make_flat() noexcept {
    return { 1.f, 0.f, 0.f, 0.f, 0.f };
}

} // namespace ivanna::dsp
