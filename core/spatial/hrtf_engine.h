#pragma once
// IVANNA Spatial Audio - Custom HRTF + Room simulation

#include <array>
#include <cstdint>
#include <cstring>
#include <cmath>

namespace ivanna::spatial {

class HRTFEngine {
public:
    bool load_hrtf(const char* user_profile) noexcept {
        if (!user_profile) return false;
        // Load user-specific HRTF (measured or selected from database)
        // Placeholder: use default HRTF
        for (size_t i = 0; i < hrtf_left_.size(); ++i) {
            float t = static_cast<float>(i) / hrtf_left_.size();
            hrtf_left_[i] = std::exp(-t * 5.0f) * std::cos(t * 20.0f);
            hrtf_right_[i] = std::exp(-t * 5.0f) * std::cos(t * 20.0f + 0.5f);
        }
        return true;
    }
    
    void process(const float* in_L, const float* in_R,
                 float* out_L, float* out_R,
                 uint32_t frames,
                 float azimuth, float elevation, float distance) noexcept {
        
        // Apply HRTF convolution (simplified FIR)
        for (uint32_t i = 0; i < frames; ++i) {
            float sum_L = 0.0f, sum_R = 0.0f;
            
            // Convolve with HRTF (limited taps for real-time)
            uint32_t taps = std::min((uint32_t)hrtf_left_.size(), i + 1);
            for (uint32_t j = 0; j < taps; ++j) {
                uint32_t idx = i - j;
                sum_L += in_L[idx] * hrtf_left_[j];
                sum_R += in_R[idx] * hrtf_right_[j];
            }
            
            // Apply distance attenuation (inverse square law)
            float attenuation = 1.0f / (1.0f + distance * distance);
            out_L[i] = sum_L * attenuation;
            out_R[i] = sum_R * attenuation;
        }
    }
    
    void set_room_params(float size, float absorption, float diffusion) noexcept {
        room_size_ = size;
        room_absorption_ = absorption;
        room_diffusion_ = diffusion;
    }

private:
    alignas(64) std::array<float, 512> hrtf_left_;
    alignas(64) std::array<float, 512> hrtf_right_;
    float room_size_ = 1.0f;
    float room_absorption_ = 0.5f;
    float room_diffusion_ = 0.7f;
};

} // namespace ivanna::spatial
