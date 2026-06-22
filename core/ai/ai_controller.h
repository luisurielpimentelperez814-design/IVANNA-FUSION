#pragma once
// IVANNA Edge AI Controller - Parameter-only, never modifies DSP core
// Fallback offline mandatory | NNAPI/SNPE preferred

#include <vector>
#include <cstdint>

namespace ivanna::ai {

struct AIParameterUpdate {
    uint32_t module_id;
    uint32_t param_id;
    float value;
    float confidence;  // 0.0 - 1.0
};

class AIController {
public:
    // AI can ONLY suggest parameter updates, never modify DSP directly
    std::vector<AIParameterUpdate> suggest_updates(
        const float* spectrum, uint32_t bins,
        const char* device_fingerprint) noexcept {
        
        std::vector<AIParameterUpdate> updates;
        
        // Auto-EQ based on spectral analysis
        if (detect_spectral_imbalance(spectrum, bins)) {
            updates.push_back({
                .module_id = 1,  // MODULE_PARAMETRIC_EQ
                .param_id = 0,   // PARAM_EQ_LOW_GAIN
                .value = compute_compensation(spectrum),
                .confidence = 0.85f
            });
        }
        
        return updates;
    }
    
    // FALLBACK: if AI fails, return default parameters (offline-safe)
    std::vector<AIParameterUpdate> get_fallback_params() const noexcept {
        return flat_response_defaults_;
    }

private:
    bool detect_spectral_imbalance(const float* spec, uint32_t bins) noexcept {
        if (!spec || bins == 0) return false;
        // Simple heuristic: check if low freqs are much higher than high freqs
        float low_energy = 0.0f, high_energy = 0.0f;
        uint32_t mid = bins / 2;
        for (uint32_t i = 0; i < mid; ++i) low_energy += spec[i] * spec[i];
        for (uint32_t i = mid; i < bins; ++i) high_energy += spec[i] * spec[i];
        return (low_energy > high_energy * 3.0f);
    }
    
    float compute_compensation(const float* spec) noexcept {
        // Simple compensation: reduce low freq gain
        return -3.0f;  // -3dB
    }
    
    std::vector<AIParameterUpdate> flat_response_defaults_;
};

} // namespace ivanna::ai
