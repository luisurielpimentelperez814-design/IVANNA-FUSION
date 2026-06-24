#pragma once
// ivanna_fusion.h — Motor DSP Principal IVANNA FUSION
// Integra: ParametricEQ → Compressor → HarmonicExciter
// + interfaz para el plugin Android Audio Effect
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "parametric_eq.h"
#include "compressor.h"
#include "harmonic_exciter.h"
#include "stereo_widener.h"
#include <cstdint>
#include <atomic>

namespace ivanna {

// ─── UUID del efecto (único, registrado en audio_effects.xml) ─────────────────
// {7b3be4ec-c23c-4e6e-8c6d-49e5f4d54ea3}
static constexpr uint32_t EFFECT_UUID_TIMEH = 0x7b3be4ec;
static constexpr uint16_t EFFECT_UUID_TIME1 = 0xc23c;
static constexpr uint16_t EFFECT_UUID_TIME2 = 0x4e6e;
static constexpr uint8_t  EFFECT_UUID_NODE[8] = {0x8c,0x6d,0x49,0xe5,0xf4,0xd5,0x4e,0xa3};

// ─── IDs de parámetros para EFFECT_CMD_SET_PARAM ─────────────────────────────
enum EffectParamId : int32_t {
    // PEQ: para banda N (0..7)
    // param = PARAM_PEQ_GAIN_BASE + N
    PARAM_PEQ_GAIN_BASE   = 0x00,  // 8 slots: 0x00..0x07  → gain_dB (float)
    PARAM_PEQ_FREQ_BASE   = 0x10,  // 8 slots: 0x10..0x17  → freq_hz (float)
    PARAM_PEQ_Q_BASE      = 0x20,  // 8 slots: 0x20..0x27  → Q (float)
    PARAM_PEQ_ENABLED_BASE= 0x30,  // 8 slots: 0x30..0x37  → enabled (int32)
    PARAM_PEQ_BYPASS      = 0x40,  // EQ bypass total (int32: 0/1)

    // Compressor
    PARAM_COMP_THRESHOLD  = 0x50,  // threshold_dB (float)
    PARAM_COMP_RATIO      = 0x51,  // ratio (float)
    PARAM_COMP_KNEE       = 0x52,  // knee_dB (float)
    PARAM_COMP_ATTACK     = 0x53,  // attack_ms (float)
    PARAM_COMP_RELEASE    = 0x54,  // release_ms (float)
    PARAM_COMP_MAKEUP     = 0x55,  // makeup_dB (float)
    PARAM_COMP_BYPASS     = 0x56,  // bypass (int32: 0/1)

    // Exciter
    PARAM_EXC_DRIVE       = 0x60,  // drive 1.0..10.0 (float)
    PARAM_EXC_MIX         = 0x61,  // mix 0.0..1.0 (float)
    PARAM_EXC_HPF_FREQ    = 0x62,  // hpf_freq_hz (float)
    PARAM_EXC_BYPASS      = 0x63,  // bypass (int32: 0/1)

    // Global
    PARAM_GLOBAL_BYPASS   = 0x70,  // bypass total (int32: 0/1)
    PARAM_GET_GAIN_DB     = 0x71,  // read-only: ganancia compresor actual
    PARAM_PRESET_LOAD     = 0x72,  // cargar preset: 0=flat, 1=rock_clasico

    // Stereo Widener (Mid-Side widening — ver stereo_widener.h para la
    // matemática correcta: side = (L-R)*0.5, NO x[n]-x[n-1])
    PARAM_WIDENER_WIDTH       = 0x80,  // width 0.0..2.0 (float)
    PARAM_WIDENER_BASSPROTECT = 0x81,  // bass protect (int32: 0/1)
    PARAM_WIDENER_BYPASS      = 0x82,  // bypass (int32: 0/1)
};

// ─── Motor principal ──────────────────────────────────────────────────────────
class IvannaFusionEngine {
public:
    IvannaFusionEngine() noexcept;

    bool init(float samplerate, int channel_count) noexcept;
    void reset() noexcept;

    // Hot path — llamado desde thread de audio de AudioFlinger
    // inL/inR pueden ser el mismo buffer que outL/outR (in-place)
    void process(const float* inL, const float* inR,
                 float* outL, float* outR, int n_frames) noexcept;

    // Setters de parámetros (thread-safe via atomics)
    bool setParam(int32_t param_id, float value) noexcept;
    bool getParam(int32_t param_id, float& value_out) noexcept;
    void loadPreset(int preset_id) noexcept;
    void setGlobalBypass(bool b) noexcept { global_bypass_.store(b); }

    // Accesores para los sub-módulos (para control directo desde JNI si aplica)
    dsp::ParametricEQ&   peq()      noexcept { return peq_; }
    dsp::Compressor&     comp()     noexcept { return comp_; }
    dsp::HarmonicExciter& exciter() noexcept { return exciter_; }
    dsp::StereoWidener&  widener()  noexcept { return widener_; }

private:
    dsp::ParametricEQ    peq_;
    dsp::Compressor      comp_;
    dsp::HarmonicExciter exciter_;
    dsp::StereoWidener   widener_;

    std::atomic<bool>    global_bypass_ { false };
    float                samplerate_   = 48000.f;
    int                  channels_     = 2;

    // Buffers intermedios para in-place seguro
    alignas(16) float buf_l_[4096];
    alignas(16) float buf_r_[4096];
};

} // namespace ivanna
