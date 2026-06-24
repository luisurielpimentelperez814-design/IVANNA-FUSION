// compressor.cpp — Compresor estéreo RMS soft-knee
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "compressor.h"
#include <cstring>
#include <cmath>
#include <algorithm>

namespace ivanna::dsp {

// ─── dB helpers ──────────────────────────────────────────────────────────────
static inline float lin_to_dB(float x) noexcept {
    return (x > 1e-10f) ? (20.f * std::log10(x)) : -200.f;
}
static inline float dB_to_lin(float x) noexcept {
    return std::pow(10.f, x / 20.f);
}

Compressor::Compressor() noexcept {
    std::memset(rms_buf_, 0, sizeof(rms_buf_));
}

void Compressor::setSamplerate(float fs) noexcept {
    samplerate_ = fs;
    // Recalcular coeficientes de attack/release
    // (invocamos setAttack/setRelease con valores por defecto si es la primera vez)
    setAttack(5.f);
    setRelease(80.f);
}

void Compressor::setAttack(float ms) noexcept {
    // alpha = exp(-ln(9) / (ms/1000 * Fs))
    // → en 1 time-constant la señal cae al 36.7%, en ln(9) alcanza -20dB
    float tau = ms * 0.001f * samplerate_;
    alpha_attack_ = (tau > 0.f) ? std::exp(-2.2f / tau) : 0.f;
}

void Compressor::setRelease(float ms) noexcept {
    float tau = ms * 0.001f * samplerate_;
    alpha_release_ = (tau > 0.f) ? std::exp(-2.2f / tau) : 0.f;
}

void Compressor::resetState() noexcept {
    std::memset(rms_buf_, 0, sizeof(rms_buf_));
    rms_idx_ = 0;
    rms_sum_ = 0.f;
    env_rms_ = 0.f;
    current_gain_lin_ = 1.f;
}

float Compressor::currentGainDB() const noexcept {
    return lin_to_dB(current_gain_lin_);
}

// ─── Ganancia de compresión (soft-knee) ──────────────────────────────────────
float Compressor::computeGain_dB(float x_dB) const noexcept {
    const float T = threshold_dB_.load(std::memory_order_relaxed);
    const float R = ratio_.load(std::memory_order_relaxed);
    const float W = knee_dB_.load(std::memory_order_relaxed);
    const float half_W = W * 0.5f;

    float y_dB;
    if (x_dB < (T - half_W)) {
        // Debajo del threshold: sin compresión
        y_dB = x_dB;
    } else if (x_dB > (T + half_W)) {
        // Sobre el threshold: compresión completa
        y_dB = T + (x_dB - T) / R;
    } else {
        // Zona de knee suave: interpolación cuadrática
        float delta = x_dB - T + half_W;
        y_dB = x_dB + (1.f/R - 1.f) * (delta * delta) / (2.f * W);
    }

    // La ganancia de compresión es la diferencia
    return y_dB - x_dB;
}

// ─── Hot path ─────────────────────────────────────────────────────────────────
void Compressor::process(const float* inL, const float* inR,
                          float* outL, float* outR, int n_frames) noexcept {
    if (bypass_.load(std::memory_order_relaxed)) {
        if (inL != outL) std::memcpy(outL, inL, n_frames * sizeof(float));
        if (inR != outR) std::memcpy(outR, inR, n_frames * sizeof(float));
        return;
    }

    const float aA = alpha_attack_;
    const float aR = alpha_release_;
    const float makeup_lin = dB_to_lin(makeup_dB_.load(std::memory_order_relaxed));

    float env    = env_rms_;
    float g_lin  = current_gain_lin_;
    float r_sum  = rms_sum_;
    int   r_idx  = rms_idx_;

    for (int i = 0; i < n_frames; ++i) {
        // ── Detección de nivel RMS (ventana deslizante) ───────────────────
        float xL = inL[i], xR = inR[i];

        // Protección NaN/Inf: si la entrada llegara corrupta (por ejemplo
        // desde una etapa anterior del pipeline), sanitizar ANTES de que
        // entre a la ventana deslizante circular (rms_buf_). Sin esto, un
        // solo sample NaN contaminaría r_sum y por tanto env_rms_ durante
        // los próximos RMS_WINDOW samples (el valor envenenado permanece
        // en el buffer circular hasta que se sobrescribe), no solo el
        // sample actual — más persistente que un NaN suelto en una
        // variable simple.
        if (std::isnan(xL) || std::isinf(xL)) xL = 0.f;
        if (std::isnan(xR) || std::isinf(xR)) xR = 0.f;

        // Valor cuadrático del par estéreo (RMS linked)
        float xSq = 0.5f * (xL*xL + xR*xR);

        // Ventana deslizante: restar valor viejo, agregar nuevo
        r_sum -= rms_buf_[r_idx];
        rms_buf_[r_idx] = xSq;
        r_sum += xSq;
        r_idx = (r_idx + 1 < RMS_WINDOW) ? (r_idx + 1) : 0;

        float level_lin = std::sqrt(std::max(r_sum / (float)RMS_WINDOW, 0.f));

        // ── Envelope follower (attack/release) ───────────────────────────
        float coeff = (level_lin > env) ? aA : aR;
        env = coeff * env + (1.f - coeff) * level_lin;

        // ── Gain computer ─────────────────────────────────────────────────
        float level_dB  = lin_to_dB(env);
        float gain_dB   = computeGain_dB(level_dB);
        float target_lin = dB_to_lin(gain_dB) * makeup_lin;

        // Suavizado de ganancia para eliminar zipper noise
        g_lin = g_lin * 0.999f + target_lin * 0.001f;

        // ── Aplicar ganancia ──────────────────────────────────────────────
        outL[i] = xL * g_lin;
        outR[i] = xR * g_lin;
    }

    env_rms_          = env;
    current_gain_lin_ = g_lin;
    rms_sum_          = r_sum;
    rms_idx_          = r_idx;
}

} // namespace ivanna::dsp
