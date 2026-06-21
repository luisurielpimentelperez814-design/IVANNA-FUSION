#pragma once
// parametric_eq.h — Ecualizador Paramétrico de 8 Bandas
// Cascada de biquads IIR con coeficientes dinámicamente actualizables
//
// Bandas predeterminadas:
//   0: Sub-bass  63 Hz  Low Shelf  S=0.7
//   1: Bass     125 Hz  Peaking    Q=1.0
//   2: Low-mid  250 Hz  Peaking    Q=1.0
//   3: Mid      500 Hz  Peaking    Q=1.0
//   4: Upper   1000 Hz  Peaking    Q=1.0
//   5: Presence 2000 Hz  Peaking   Q=1.2
//   6: Brilliance 4000 Hz  Peaking Q=1.2
//   7: Air      8000 Hz  High Shelf S=0.7
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "biquad_neon.h"
#include <array>
#include <atomic>
#include <cstdint>

namespace ivanna::dsp {

static constexpr int PEQ_BANDS = 8;

enum class BandType : uint8_t {
    PEAKING     = 0,
    LOW_SHELF   = 1,
    HIGH_SHELF  = 2,
    LOWPASS     = 3,
    HIGHPASS    = 4,
};

struct PeqBandParams {
    float       frequency_hz = 1000.f;
    float       Q_or_slope   = 1.0f;    // Q para peaking/LP/HP, S (shelf slope) para shelves
    float       gain_dB      = 0.0f;    // Ganancia en dB
    BandType    type         = BandType::PEAKING;
    bool        enabled      = true;
};

class ParametricEQ {
public:
    ParametricEQ() noexcept;

    // Configurar samplerate (recalcula todos los coeficientes)
    void setSamplerate(float fs) noexcept;

    // Actualizar parámetros de una banda (thread-safe: double-buffer)
    void setBand(int band, const PeqBandParams& params) noexcept;

    // Actualizar ganancia de una banda rápidamente
    void setBandGain(int band, float gain_dB) noexcept;

    // Habilitar/deshabilitar bypass total
    void setBypass(bool bypass) noexcept { bypass_.store(bypass, std::memory_order_relaxed); }

    // Resetear estados internos (evitar click al reanudar)
    void resetState() noexcept;

    // Procesar bloque estéreo — hot path, no llames desde UI thread
    void process(const float* inL, const float* inR,
                 float* outL, float* outR, int n_frames) noexcept;

    // Acceso a params actuales (para UI)
    const PeqBandParams& getBand(int band) const noexcept { return params_[band]; }
    float getSamplerate() const noexcept { return samplerate_; }

private:
    void recalcBand(int band) noexcept;
    void recalcAll() noexcept;

    float                                    samplerate_   = 48000.f;
    std::array<PeqBandParams, PEQ_BANDS>     params_;
    std::array<BiquadCoeffs, PEQ_BANDS>      coeffs_;
    std::array<BiquadStateStereo, PEQ_BANDS> states_;
    std::atomic<bool>                        bypass_       { false };
};

// ─── Preset por defecto (flat, 0 dB en todas las bandas) ─────────────────────
inline std::array<PeqBandParams, PEQ_BANDS> peq_default_params() noexcept {
    return {{
        { 63.f,   0.7f, 0.f, BandType::LOW_SHELF,  true },
        { 125.f,  1.0f, 0.f, BandType::PEAKING,    true },
        { 250.f,  1.0f, 0.f, BandType::PEAKING,    true },
        { 500.f,  1.0f, 0.f, BandType::PEAKING,    true },
        { 1000.f, 1.0f, 0.f, BandType::PEAKING,    true },
        { 2000.f, 1.2f, 0.f, BandType::PEAKING,    true },
        { 4000.f, 1.2f, 0.f, BandType::PEAKING,    true },
        { 8000.f, 0.7f, 0.f, BandType::HIGH_SHELF, true },
    }};
}

// ─── Preset "Rock Clásico" calibrado para Rush/Grand Funk Railroad ────────────
inline std::array<PeqBandParams, PEQ_BANDS> peq_preset_classic_rock() noexcept {
    return {{
        { 63.f,   0.7f, +2.5f, BandType::LOW_SHELF,  true },  // sub-bass warm
        { 125.f,  1.0f, +1.5f, BandType::PEAKING,    true },  // kick/bass punch
        { 250.f,  1.0f, -1.0f, BandType::PEAKING,    true },  // mud cut
        { 500.f,  1.0f, +0.5f, BandType::PEAKING,    true },  // body
        { 1000.f, 1.0f,  0.0f, BandType::PEAKING,    true },
        { 2000.f, 1.2f, +2.0f, BandType::PEAKING,    true },  // guitar crunch
        { 4000.f, 1.2f, +1.5f, BandType::PEAKING,    true },  // pick attack
        { 8000.f, 0.7f, +1.5f, BandType::HIGH_SHELF, true },  // cymbal air
    }};
}

} // namespace ivanna::dsp
