#pragma once
// IVANNA DSP CORE - Zero-allocation Biquad with ARM NEON / x86 SSE scalar
// Complexity Level: 2/5 | CPU Budget: 0.3ms/block | Memory: 64B per instance

#include <array>
#include <cstdint>
#include <cmath>

#if defined(__aarch64__) || defined(__arm__) || defined(IVANNA_SIMD_NEON)
#  include <arm_neon.h>
#  define IVANNA_BIQUAD_NEON 1
#elif defined(IVANNA_SIMD_AVX2) || defined(IVANNA_SIMD_SSE2)
#  include <immintrin.h>
#  define IVANNA_BIQUAD_X86 1
#endif

namespace ivanna::dsp {

#if defined(IVANNA_BIQUAD_NEON)

struct alignas(16) BiquadCoeffs {
    float32x4_t b;  // b0, b1, b2, 0
    float32x4_t a;  // a1, a2, 0, 0 (a0 normalizado a 1)
};

struct alignas(32) BiquadState {
    float32x4_t x_hist;  // x[n-1], x[n-2], 0, 0
    float32x4_t y_hist;  // y[n-1], y[n-2], 0, 0
};

class BiquadFilter {
public:
    inline void process(const float* in, float* out, uint32_t frames) noexcept {
        float32x4_t b = coeffs_.b;
        float32x4_t a = coeffs_.a;
        const float b0 = vgetq_lane_f32(b, 0);
        const float b1 = vgetq_lane_f32(b, 1);
        const float b2 = vgetq_lane_f32(b, 2);
        const float a1 = vgetq_lane_f32(a, 0);
        const float a2 = vgetq_lane_f32(a, 1);
        float xm1 = vgetq_lane_f32(state_.x_hist, 0);
        float xm2 = vgetq_lane_f32(state_.x_hist, 1);
        float ym1 = vgetq_lane_f32(state_.y_hist, 0);
        float ym2 = vgetq_lane_f32(state_.y_hist, 1);
        for (uint32_t i = 0; i < frames; ++i) {
            float x = in[i];
            float y = b0*x + b1*xm1 + b2*xm2 - a1*ym1 - a2*ym2;
            out[i] = y;
            xm2 = xm1; xm1 = x;
            ym2 = ym1; ym1 = y;
        }
        state_.x_hist = vsetq_lane_f32(xm2, vsetq_lane_f32(xm1, vdupq_n_f32(0.f), 0), 1);
        state_.y_hist = vsetq_lane_f32(ym2, vsetq_lane_f32(ym1, vdupq_n_f32(0.f), 0), 1);
    }

    void set_coeffs(float b0, float b1, float b2, float a1, float a2) noexcept {
        float b_arr[4] = {b0, b1, b2, 0.f};
        float a_arr[4] = {a1, a2, 0.f, 0.f};
        coeffs_.b = vld1q_f32(b_arr);
        coeffs_.a = vld1q_f32(a_arr);
    }

    void reset() noexcept {
        state_.x_hist = vdupq_n_f32(0.f);
        state_.y_hist = vdupq_n_f32(0.f);
    }

private:
    BiquadCoeffs coeffs_{};
    BiquadState  state_{};
};

#else  // Scalar fallback — funciona en x86_64 (CI runner) y cualquier arq

struct BiquadCoeffs {
    float b0 = 1.f, b1 = 0.f, b2 = 0.f;
    float a1 = 0.f, a2 = 0.f;
};

struct BiquadState {
    float xm1 = 0.f, xm2 = 0.f;
    float ym1 = 0.f, ym2 = 0.f;
};

class BiquadFilter {
public:
    inline void process(const float* in, float* out, uint32_t frames) noexcept {
        float b0=c_.b0, b1=c_.b1, b2=c_.b2, a1=c_.a1, a2=c_.a2;
        float xm1=s_.xm1, xm2=s_.xm2, ym1=s_.ym1, ym2=s_.ym2;
        for (uint32_t i = 0; i < frames; ++i) {
            float x = in[i];
            float y = b0*x + b1*xm1 + b2*xm2 - a1*ym1 - a2*ym2;
            out[i] = y;
            xm2=xm1; xm1=x; ym2=ym1; ym1=y;
        }
        s_ = {xm1, xm2, ym1, ym2};
    }

    void set_coeffs(float b0, float b1, float b2, float a1, float a2) noexcept {
        c_ = {b0, b1, b2, a1, a2};
    }

    void reset() noexcept { s_ = {}; }

private:
    BiquadCoeffs c_;
    BiquadState  s_;
};

#endif  // IVANNA_BIQUAD_NEON / scalar

} // namespace ivanna::dsp
