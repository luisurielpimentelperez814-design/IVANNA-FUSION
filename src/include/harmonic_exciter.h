#pragma once
// harmonic_exciter.h — Excitador Armónico
//
// Genera armónicos pares e impares mediante waveshaping tanh,
// los aísla con un HPF y los mezcla de vuelta al original.
// Resultado: presencia y brillo sin subir el volumen general.
//
// Pipeline:
//   x ──┬──────────────────────────────────────────► (+)──► y
//       │                                              ↑
//       └─► HPF ─► tanh(drive * x) ─► HPF ─► gain ──┘
//                  (waveshaping)       (solo armónicos nuevos)
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "biquad_neon.h"
#include <atomic>
#include <cmath>

namespace ivanna::dsp {

class HarmonicExciter {
public:
    HarmonicExciter() noexcept;

    void setSamplerate(float fs) noexcept;

    // drive: 1.0 = suave, 10.0 = agresivo
    void setDrive(float drive) noexcept  { drive_.store(drive, std::memory_order_relaxed); }

    // mix: cantidad de armónicos añadidos (0.0 = nada, 1.0 = máximo)
    void setMix(float mix) noexcept      { mix_.store(mix, std::memory_order_relaxed); }

    // Frecuencia de corte del HPF de presencia (default 2kHz)
    void setHpfFreq(float hz) noexcept;

    void setBypass(bool b) noexcept      { bypass_.store(b, std::memory_order_relaxed); }
    void resetState() noexcept;

    void process(const float* inL, const float* inR,
                 float* outL, float* outR, int n_frames) noexcept;

private:
    // waveshaping: tanh suave normalizado
    static inline float waveshape(float x, float drive) noexcept {
        // Normalizado para que gain = 1 en señal pequeña
        // tanh(drive*x) / tanh(drive)
        float d_x = drive * x;
        // Clamp para evitar overflow en tanh
        if (d_x >  10.f) d_x =  10.f;
        if (d_x < -10.f) d_x = -10.f;
        static const float inv_tanh_drive_cache = 1.f; // se recalcula en setDrive
        return std::tanh(d_x);
    }

    float samplerate_ = 48000.f;

    std::atomic<float> drive_  { 3.f };
    std::atomic<float> mix_    { 0.25f };
    std::atomic<bool>  bypass_ { false };

    float tanh_norm_inv_ = 1.f;   // 1/tanh(drive) para normalizar ganancia

    // HPF antes del waveshaper: elimina sub-frecuencias para no distorsionar bajos
    BiquadCoeffs  hpf_pre_coeffs_;
    BiquadStateStereo hpf_pre_state_;

    // HPF después del waveshaper: extrae solo los armónicos nuevos
    BiquadCoeffs  hpf_post_coeffs_;
    BiquadStateStereo hpf_post_state_;

    float hpf_freq_ = 2000.f;
};

} // namespace ivanna::dsp
