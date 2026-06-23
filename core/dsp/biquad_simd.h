#pragma once
#include "ivanna/core/SimdDefs.h"
#include <cstddef>
#include <algorithm>

namespace ivanna::dsp {

class BiquadFilterSimd {
public:
    struct Coeffs {
        float b0, b1, b2, a1, a2;
    };

    void setCoeffs(const Coeffs& c) noexcept { coeffs_ = c; }

    void processBlock(float* data, size_t n) noexcept {
        const float b0 = coeffs_.b0;
        const float b1 = coeffs_.b1;
        const float b2 = coeffs_.b2;
        const float a1 = coeffs_.a1;
        const float a2 = coeffs_.a2;
        
        float s1 = state_[0];
        float s2 = state_[1];

        size_t i = 0;
        for (; i + 4 <= n; i += 4) {
            float x0 = data[i];
            float y0 = b0 * x0 + s1;
            s1 = b1 * x0 - a1 * y0 + s2;
            s2 = b2 * x0 - a2 * y0;
            data[i] = y0;

            float x1 = data[i+1];
            float y1 = b0 * x1 + s1;
            s1 = b1 * x1 - a1 * y1 + s2;
            s2 = b2 * x1 - a2 * y1;
            data[i+1] = y1;

            float x2 = data[i+2];
            float y2 = b0 * x2 + s1;
            s1 = b1 * x2 - a1 * y2 + s2;
            s2 = b2 * x2 - a2 * y2;
            data[i+2] = y2;

            float x3 = data[i+3];
            float y3 = b0 * x3 + s1;
            s1 = b1 * x3 - a1 * y3 + s2;
            s2 = b2 * x3 - a2 * y3;
            data[i+3] = y3;
        }

        for (; i < n; ++i) {
            float x = data[i];
            float y = b0 * x + s1;
            s1 = b1 * x - a1 * y + s2;
            s2 = b2 * x - a2 * y;
            data[i] = y;
        }

        state_[0] = s1;
        state_[1] = s2;
    }

    void processBlockMultiChannel(float** channels, size_t numChannels, size_t n) noexcept {
#if defined(IVANNA_SIMD_NEON)
        processBlockMultiChannelNeon(channels, numChannels, n);
#elif defined(IVANNA_SIMD_SSE2) || defined(IVANNA_SIMD_AVX2)
        processBlockMultiChannelSse(channels, numChannels, n);
#else
        for (size_t c = 0; c < numChannels; ++c) {
            processBlock(channels[c], n);
        }
#endif
    }

    void reset() noexcept { state_[0] = state_[1] = 0.0f; }

private:
    Coeffs coeffs_{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    float state_[2]{0.0f, 0.0f};

#if defined(IVANNA_SIMD_NEON)
    void processBlockMultiChannelNeon(float** channels, size_t numChannels, size_t n) noexcept {
        const float32x4_t v_b0 = vdupq_n_f32(coeffs_.b0);
        const float32x4_t v_b1 = vdupq_n_f32(coeffs_.b1);
        const float32x4_t v_b2 = vdupq_n_f32(coeffs_.b2);
        const float32x4_t v_a1 = vdupq_n_f32(coeffs_.a1);
        const float32x4_t v_a2 = vdupq_n_f32(coeffs_.a2);

        float32x4_t v_s1 = vdupq_n_f32(state_[0]);
        float32x4_t v_s2 = vdupq_n_f32(state_[1]);

        for (size_t i = 0; i < n; ++i) {
            float x_arr[4] = {0, 0, 0, 0};
            for (size_t c = 0; c < std::min(numChannels, size_t(4)); ++c) {
                x_arr[c] = channels[c][i];
            }
            float32x4_t v_x = vld1q_f32(x_arr);

            float32x4_t v_y = vmlaq_f32(v_s1, v_b0, v_x);
            float32x4_t v_s1_new = vmlaq_f32(v_s2, v_b1, v_x);
            v_s1_new = vmlsq_f32(v_s1_new, v_a1, v_y);
            float32x4_t v_s2_new = vmulq_f32(v_b2, v_x);
            v_s2_new = vmlsq_f32(v_s2_new, v_a2, v_y);

            v_s1 = v_s1_new;
            v_s2 = v_s2_new;

            float y_arr[4];
            vst1q_f32(y_arr, v_y);
            for (size_t c = 0; c < std::min(numChannels, size_t(4)); ++c) {
                channels[c][i] = y_arr[c];
            }
        }

        float final_s1[4], final_s2[4];
        vst1q_f32(final_s1, v_s1);
        vst1q_f32(final_s2, v_s2);
        state_[0] = final_s1[0];
        state_[1] = final_s2[0];

        for (size_t c = 4; c < numChannels; ++c) {
            processBlock(channels[c], n);
        }
    }
#endif

#if defined(IVANNA_SIMD_SSE2) || defined(IVANNA_SIMD_AVX2)
    void processBlockMultiChannelSse(float** channels, size_t numChannels, size_t n) noexcept {
        const __m128 v_b0 = _mm_set1_ps(coeffs_.b0);
        const __m128 v_b1 = _mm_set1_ps(coeffs_.b1);
        const __m128 v_b2 = _mm_set1_ps(coeffs_.b2);
        const __m128 v_a1 = _mm_set1_ps(coeffs_.a1);
        const __m128 v_a2 = _mm_set1_ps(coeffs_.a2);
        __m128 v_s1 = _mm_set1_ps(state_[0]);
        __m128 v_s2 = _mm_set1_ps(state_[1]);

        for (size_t i = 0; i < n; ++i) {
            float x_arr[4] = {0, 0, 0, 0};
            for (size_t c = 0; c < std::min(numChannels, size_t(4)); ++c) {
                x_arr[c] = channels[c][i];
            }
            __m128 v_x = _mm_loadu_ps(x_arr);

            __m128 v_y = _mm_add_ps(v_s1, _mm_mul_ps(v_b0, v_x));
            __m128 v_s1_new = _mm_add_ps(v_s2, _mm_mul_ps(v_b1, v_x));
            v_s1_new = _mm_sub_ps(v_s1_new, _mm_mul_ps(v_a1, v_y));
            __m128 v_s2_new = _mm_sub_ps(_mm_mul_ps(v_b2, v_x), _mm_mul_ps(v_a2, v_y));

            v_s1 = v_s1_new;
            v_s2 = v_s2_new;

            float y_arr[4];
            _mm_storeu_ps(y_arr, v_y);
            for (size_t c = 0; c < std::min(numChannels, size_t(4)); ++c) {
                channels[c][i] = y_arr[c];
            }
        }

        float final_s1[4], final_s2[4];
        _mm_storeu_ps(final_s1, v_s1);
        _mm_storeu_ps(final_s2, v_s2);
        state_[0] = final_s1[0];
        state_[1] = final_s2[0];

        for (size_t c = 4; c < numChannels; ++c) {
            processBlock(channels[c], n);
        }
    }
#endif
};

} // namespace ivanna::dsp
