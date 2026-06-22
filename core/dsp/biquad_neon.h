#pragma once
// IVANNA DSP CORE - Zero-allocation Biquad with ARM NEON
// Complexity Level: 2/5 | CPU Budget: 0.3ms/block | Memory: 64B per instance

#include <arm_neon.h>
#include <array>
#include <cstdint>

namespace ivanna::dsp {

struct alignas(16) BiquadCoeffs {
    float32x4_t b;  // b0, b1, b2, 0
    float32x4_t a;  // a1, a2, 0, 0 (a0 normalized to 1)
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
        
        float x_n1 = vgetq_lane_f32(state_.x_hist, 0);
        float x_n2 = vgetq_lane_f32(state_.x_hist, 1);
        float y_n1 = vgetq_lane_f32(state_.y_hist, 0);
        float y_n2 = vgetq_lane_f32(state_.y_hist, 1);
        
        uint32_t i = 0;
        for (; i + 4 <= frames; i += 4) {
            float32x4_t x_vec = vld1q_f32(&in[i]);
            float32x4_t y_vec;
            
            float x0 = vgetq_lane_f32(x_vec, 0);
            float y0 = b0*x0 + b1*x_n1 + b2*x_n2 - a1*y_n1 - a2*y_n2;
            float x1 = vgetq_lane_f32(x_vec, 1);
            float y1 = b0*x1 + b1*x0  + b2*x_n1 - a1*y0  - a2*y_n1;
            float x2 = vgetq_lane_f32(x_vec, 2);
            float y2 = b0*x2 + b1*x1  + b2*x0   - a1*y1  - a2*y0;
            float x3 = vgetq_lane_f32(x_vec, 3);
            float y3 = b0*x3 + b1*x2  + b2*x1   - a1*y2  - a2*y1;
            
            y_vec = vsetq_lane_f32(y0, y_vec, 0);
            y_vec = vsetq_lane_f32(y1, y_vec, 1);
            y_vec = vsetq_lane_f32(y2, y_vec, 2);
            y_vec = vsetq_lane_f32(y3, y_vec, 3);
            
            vst1q_f32(&out[i], y_vec);
            x_n1 = x3; x_n2 = x2; y_n1 = y3; y_n2 = y2;
        }
        
        for (; i < frames; ++i) {
            float x = in[i];
            float y = b0*x + b1*x_n1 + b2*x_n2 - a1*y_n1 - a2*y_n2;
            out[i] = y;
            x_n2 = x_n1; x_n1 = x;
            y_n2 = y_n1; y_n1 = y;
        }
        
        state_.x_hist = vsetq_lane_f32(x_n1, state_.x_hist, 0);
        state_.x_hist = vsetq_lane_f32(x_n2, state_.x_hist, 1);
        state_.y_hist = vsetq_lane_f32(y_n1, state_.y_hist, 0);
        state_.y_hist = vsetq_lane_f32(y_n2, state_.y_hist, 1);
    }
    
    void set_coeffs(float b0, float b1, float b2, float a1, float a2) noexcept {
        coeffs_.b = vsetq_lane_f32(b0, coeffs_.b, 0);
        coeffs_.b = vsetq_lane_f32(b1, coeffs_.b, 1);
        coeffs_.b = vsetq_lane_f32(b2, coeffs_.b, 2);
        coeffs_.a = vsetq_lane_f32(a1, coeffs_.a, 0);
        coeffs_.a = vsetq_lane_f32(a2, coeffs_.a, 1);
    }

private:
    BiquadCoeffs coeffs_{};
    BiquadState state_{};
};

} // namespace ivanna::dsp
