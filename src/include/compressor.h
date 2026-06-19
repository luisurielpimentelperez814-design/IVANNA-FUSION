#pragma once
// compressor.h — Compresor Estéreo de Knee Suave
// Detección RMS (sliding window), knee suave, attack/release en dominio log
//
// Parámetros:
//   threshold_dB : nivel de activación (ej. -18 dB)
//   ratio        : relación de compresión (ej. 4:1)
//   knee_dB      : ancho de la zona de transición suave (ej. 6 dB)
//   attack_ms    : tiempo de attack (ej. 5 ms)
//   release_ms   : tiempo de release (ej. 80 ms)
//   makeup_dB    : ganancia de maquillaje post-compresión
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <cmath>
#include <atomic>
#include <algorithm>

namespace ivanna::dsp {

class Compressor {
public:
    Compressor() noexcept;

    void setSamplerate(float fs) noexcept;
    void setThreshold(float dB) noexcept  { threshold_dB_.store(dB, std::memory_order_relaxed); }
    void setRatio(float ratio) noexcept   { ratio_.store(ratio, std::memory_order_relaxed); }
    void setKnee(float dB) noexcept       { knee_dB_.store(dB, std::memory_order_relaxed); }
    void setAttack(float ms) noexcept;
    void setRelease(float ms) noexcept;
    void setMakeup(float dB) noexcept     { makeup_dB_.store(dB, std::memory_order_relaxed); }
    void setBypass(bool b) noexcept       { bypass_.store(b, std::memory_order_relaxed); }
    void resetState() noexcept;

    // Retorna la ganancia de compresión actual (para VU meter, lineal)
    float currentGain() const noexcept    { return current_gain_lin_; }
    float currentGainDB() const noexcept;

    // Procesar bloque estéreo — linked stereo (un gain para ambos canales)
    void process(const float* inL, const float* inR,
                 float* outL, float* outR, int n_frames) noexcept;

private:
    // Ganancia de compresión en dB (soft-knee compute)
    float computeGain_dB(float level_dB) const noexcept;

    float samplerate_      = 48000.f;
    float alpha_attack_    = 0.f;      // coeficiente de attack  (0..1)
    float alpha_release_   = 0.f;      // coeficiente de release (0..1)

    // Parámetros atómicos (thread-safe para cambios desde UI)
    std::atomic<float> threshold_dB_ { -18.f };
    std::atomic<float> ratio_        {   4.f  };
    std::atomic<float> knee_dB_      {   6.f  };
    std::atomic<float> makeup_dB_    {   3.f  };
    std::atomic<bool>  bypass_       { false   };

    // Estado del envelope follower
    float env_rms_       = 0.f;   // RMS level estimado
    float current_gain_lin_ = 1.f;

    // RMS sliding window — circular buffer de 10ms
    static constexpr int RMS_WINDOW = 480;  // a 48kHz
    float rms_buf_[RMS_WINDOW] = {};
    int   rms_idx_  = 0;
    float rms_sum_  = 0.f;
};

} // namespace ivanna::dsp
