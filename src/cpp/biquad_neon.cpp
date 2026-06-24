// biquad_neon.cpp — Implementación biquad IIR con ARM NEON SIMD
// Direct Form II Transposed
// Coeficientes: Audio EQ Cookbook (R.A. Bristow-Johnson)
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "biquad_neon.h"
#include <cstring>
#include <algorithm>
#include <cmath>

namespace ivanna::dsp {

// ─────────────────────────────────────────────────────────────────────────────
// Proceso escalar mono — fallback sin NEON
// ─────────────────────────────────────────────────────────────────────────────
void biquad_process_block_mono(const BiquadCoeffs& c, BiquadState& s,
                                const float* in, float* out, int n) noexcept {
    float w1 = s.w1;
    float w2 = s.w2;
    const float b0 = c.b0, b1 = c.b1, b2 = c.b2;
    const float a1 = c.a1, a2 = c.a2;

    for (int i = 0; i < n; ++i) {
        float x = in[i];
        float y = b0 * x + w1;
        w1       = b1 * x - a1 * y + w2;
        w2       = b2 * x - a2 * y;

        // Protección NaN/Inf en el estado del filtro: a diferencia de un
        // valor escalar simple, w1/w2 son el estado RECURSIVO del filtro
        // IIR — si se contaminan una sola vez, TODAS las muestras
        // siguientes salen NaN indefinidamente (no solo durante una
        // ventana fija, como en el envelope del compresor), porque cada
        // y depende de w1/w2 del paso anterior. Con 8 bandas en cascada
        // (ver parametric_eq.cpp), un NaN en la banda 0 contaminaría
        // las 7 bandas siguientes en el mismo bloque de proceso.
        if (std::isnan(w1) || std::isinf(w1)) w1 = 0.f;
        if (std::isnan(w2) || std::isinf(w2)) w2 = 0.f;
        if (std::isnan(y)  || std::isinf(y))  y  = 0.f;

        out[i]   = y;
    }
    s.w1 = w1;
    s.w2 = w2;
}

// ─────────────────────────────────────────────────────────────────────────────
// Proceso estéreo — NEON path (ARM64) o fallback escalar (x86/riscv)
// ─────────────────────────────────────────────────────────────────────────────
void biquad_process_block_stereo(const BiquadCoeffs& c, BiquadStateStereo& s,
                                  const float* __restrict inL, const float* __restrict inR,
                                  float* __restrict outL, float* __restrict outR,
                                  int n) noexcept {
#if defined(__aarch64__)
    // ── NEON: procesa L y R como float32x2_t ──────────────────────────────
    // Direct Form II Transposed:
    //   y  = b0*x + w1
    //   w1 = b1*x - a1*y + w2
    //   w2 = b2*x - a2*y
    float32x2_t w1v  = vld1_f32(s.w1);   // [w1L, w1R]
    float32x2_t w2v  = vld1_f32(s.w2);   // [w2L, w2R]

    const float32x2_t b0v = vdup_n_f32(c.b0);
    const float32x2_t b1v = vdup_n_f32(c.b1);
    const float32x2_t b2v = vdup_n_f32(c.b2);
    const float32x2_t a1v = vdup_n_f32(c.a1);
    const float32x2_t a2v = vdup_n_f32(c.a2);

    for (int i = 0; i < n; ++i) {
        // Cargar par estéreo
        float32x2_t xv = { inL[i], inR[i] };

        // y = b0*x + w1
        float32x2_t yv = vmla_f32(w1v, xv, b0v);   // FMA: y = w1 + b0*x

        // w1_new = b1*x - a1*y + w2
        // Usando vmls (multiply-subtract): a - b*c
        float32x2_t new_w1 = vmla_f32(vmls_f32(w2v, yv, a1v), xv, b1v);
        //                    = w2 - a1*y + b1*x

        // w2_new = b2*x - a2*y
        float32x2_t new_w2 = vmls_f32(vmul_f32(xv, b2v), yv, a2v);
        //                    = b2*x - a2*y

        // Protección NaN/Inf vectorizada: w1/w2 son el estado RECURSIVO
        // del filtro — contaminados una vez, TODAS las muestras
        // siguientes salen NaN indefinidamente (no solo una ventana
        // fija), porque cada y depende del w1/w2 del paso anterior. Con
        // 8 bandas en cascada (parametric_eq.cpp), una banda contaminada
        // arrastraría las 7 siguientes en el mismo bloque.
        //
        // NEON no tiene un intrínseco directo "isnan". Se usa el truco
        // estándar: para cualquier float finito, x == x es verdadero;
        // para NaN, x == x es SIEMPRE falso (por definición IEEE 754).
        // vceq_f32 compara lane a lane; donde la comparación es falsa
        // (NaN), la máscara es 0 y vbsl selecciona el valor "seguro" (0)
        // en su lugar. Inf no es detectado por x==x, pero std::isinf
        // tampoco se vectoriza simple en NEON sin un umbral de
        // magnitud — se acepta este límite: la causa más común y
        // realista de corrupción en este pipeline es NaN propagado
        // desde una etapa anterior, no un Inf generado internamente
        // por estos biquads (coeficientes acotados, ver
        // parametric_eq.cpp::peq_default_params).
        float32x2_t zero = vdup_n_f32(0.f);
        uint32x2_t w1_finite = vceq_f32(new_w1, new_w1);
        uint32x2_t w2_finite = vceq_f32(new_w2, new_w2);
        uint32x2_t y_finite  = vceq_f32(yv, yv);
        new_w1 = vbsl_f32(w1_finite, new_w1, zero);
        new_w2 = vbsl_f32(w2_finite, new_w2, zero);
        yv     = vbsl_f32(y_finite,  yv,     zero);

        w1v = new_w1;
        w2v = new_w2;

        outL[i] = vget_lane_f32(yv, 0);
        outR[i] = vget_lane_f32(yv, 1);
    }

    vst1_f32(s.w1, w1v);
    vst1_f32(s.w2, w2v);

#else
    // ── Fallback escalar para arquitecturas no-ARM ─────────────────────────
    float w1L = s.w1[0], w2L = s.w2[0];
    float w1R = s.w1[1], w2R = s.w2[1];
    const float b0=c.b0, b1=c.b1, b2=c.b2, a1=c.a1, a2=c.a2;

    for (int i = 0; i < n; ++i) {
        float xL = inL[i], xR = inR[i];

        float yL = b0*xL + w1L;
        w1L = b1*xL - a1*yL + w2L;
        w2L = b2*xL - a2*yL;

        float yR = b0*xR + w1R;
        w1R = b1*xR - a1*yR + w2R;
        w2R = b2*xR - a2*yR;

        outL[i] = yL;
        outR[i] = yR;
    }

    s.w1[0] = w1L; s.w2[0] = w2L;
    s.w1[1] = w1R; s.w2[1] = w2R;
#endif
}

// ─────────────────────────────────────────────────────────────────────────────
// Generadores de coeficientes (Audio EQ Cookbook)
// ─────────────────────────────────────────────────────────────────────────────
static constexpr float kPi = 3.14159265358979323846f;

BiquadCoeffs make_peaking(float f0, float Q, float dBgain, float fs) noexcept {
    // A  = sqrt( 10^(dBgain/20) ) = 10^(dBgain/40)
    float A  = std::pow(10.f, dBgain / 40.f);
    float w0 = 2.f * kPi * f0 / fs;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    float alpha = sinw0 / (2.f * Q);

    float b0 =  1.f + alpha * A;
    float b1 = -2.f * cosw0;
    float b2 =  1.f - alpha * A;
    float a0 =  1.f + alpha / A;
    float a1 = -2.f * cosw0;
    float a2 =  1.f - alpha / A;

    float inv_a0 = 1.f / a0;
    return { b0*inv_a0, b1*inv_a0, b2*inv_a0, a1*inv_a0, a2*inv_a0 };
}

BiquadCoeffs make_low_shelf(float f0, float S, float dBgain, float fs) noexcept {
    float A  = std::pow(10.f, dBgain / 40.f);
    float w0 = 2.f * kPi * f0 / fs;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    // alpha = sinw0/2 * sqrt((A+1/A)*(1/S-1)+2)
    float alpha = (sinw0 / 2.f) * std::sqrt((A + 1.f/A) * (1.f/S - 1.f) + 2.f);
    float sqA2  = 2.f * std::sqrt(A) * alpha;

    float b0 =       A * ((A+1.f) - (A-1.f)*cosw0 + sqA2);
    float b1 =  2.f * A * ((A-1.f) - (A+1.f)*cosw0);
    float b2 =       A * ((A+1.f) - (A-1.f)*cosw0 - sqA2);
    float a0 =           (A+1.f) + (A-1.f)*cosw0 + sqA2;
    float a1 =    -2.f * ((A-1.f) + (A+1.f)*cosw0);
    float a2 =           (A+1.f) + (A-1.f)*cosw0 - sqA2;

    float inv_a0 = 1.f / a0;
    return { b0*inv_a0, b1*inv_a0, b2*inv_a0, a1*inv_a0, a2*inv_a0 };
}

BiquadCoeffs make_high_shelf(float f0, float S, float dBgain, float fs) noexcept {
    float A  = std::pow(10.f, dBgain / 40.f);
    float w0 = 2.f * kPi * f0 / fs;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    float alpha = (sinw0 / 2.f) * std::sqrt((A + 1.f/A) * (1.f/S - 1.f) + 2.f);
    float sqA2  = 2.f * std::sqrt(A) * alpha;

    float b0 =       A * ((A+1.f) + (A-1.f)*cosw0 + sqA2);
    float b1 = -2.f * A * ((A-1.f) + (A+1.f)*cosw0);
    float b2 =       A * ((A+1.f) + (A-1.f)*cosw0 - sqA2);
    float a0 =           (A+1.f) - (A-1.f)*cosw0 + sqA2;
    float a1 =     2.f * ((A-1.f) - (A+1.f)*cosw0);
    float a2 =           (A+1.f) - (A-1.f)*cosw0 - sqA2;

    float inv_a0 = 1.f / a0;
    return { b0*inv_a0, b1*inv_a0, b2*inv_a0, a1*inv_a0, a2*inv_a0 };
}

BiquadCoeffs make_lowpass(float f0, float Q, float fs) noexcept {
    float w0 = 2.f * kPi * f0 / fs;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    float alpha = sinw0 / (2.f * Q);

    float b0 = (1.f - cosw0) / 2.f;
    float b1 =  1.f - cosw0;
    float b2 = (1.f - cosw0) / 2.f;
    float a0 =  1.f + alpha;
    float a1 = -2.f * cosw0;
    float a2 =  1.f - alpha;

    float inv_a0 = 1.f / a0;
    return { b0*inv_a0, b1*inv_a0, b2*inv_a0, a1*inv_a0, a2*inv_a0 };
}

BiquadCoeffs make_highpass(float f0, float Q, float fs) noexcept {
    float w0 = 2.f * kPi * f0 / fs;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    float alpha = sinw0 / (2.f * Q);

    float b0 =  (1.f + cosw0) / 2.f;
    float b1 = -(1.f + cosw0);
    float b2 =  (1.f + cosw0) / 2.f;
    float a0 =   1.f + alpha;
    float a1 =  -2.f * cosw0;
    float a2 =   1.f - alpha;

    float inv_a0 = 1.f / a0;
    return { b0*inv_a0, b1*inv_a0, b2*inv_a0, a1*inv_a0, a2*inv_a0 };
}

BiquadCoeffs make_allpass(float f0, float Q, float fs) noexcept {
    float w0 = 2.f * kPi * f0 / fs;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    float alpha = sinw0 / (2.f * Q);

    float b0 =  1.f - alpha;
    float b1 = -2.f * cosw0;
    float b2 =  1.f + alpha;
    float a0 =  1.f + alpha;
    float a1 = -2.f * cosw0;
    float a2 =  1.f - alpha;

    float inv_a0 = 1.f / a0;
    return { b0*inv_a0, b1*inv_a0, b2*inv_a0, a1*inv_a0, a2*inv_a0 };
}

} // namespace ivanna::dsp
