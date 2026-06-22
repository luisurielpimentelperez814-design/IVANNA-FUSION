#pragma once
// IVANNA Cross-Device Consistency - Perceptual normalization

#include <cstdint>
#include <cstring>

namespace ivanna::consistency {

struct DeviceFingerprint {
    char model[64];
    char hal_version[32];
    float freq_response[256];
    float thd_profile[64];
    uint32_t speaker_class;  // 0=phone, 1=headphone, 2=speaker, 3=car
    float bass_extension_hz;
    float treble_roll_off_hz;
};

class ConsistencyEngine {
public:
    bool load_fingerprint(const char* device_id) noexcept {
        if (!device_id) return false;
        // Load from file or database (placeholder)
        std::strncpy(current_fp_.model, device_id, sizeof(current_fp_.model) - 1);
        current_fp_.model[sizeof(current_fp_.model) - 1] = '\0';
        return true;
    }
    
    void compute_compensation(const DeviceFingerprint& fp,
                              float* eq_gains, uint32_t bands) noexcept {
        // Inverse of measured FR with perceptual weighting (Fletcher-Munson)
        for (uint32_t i = 0; i < bands; ++i) {
            float target_db = 0.0f;  // Flat target
            float measured_db = fp.freq_response[i];
            float perceptual_weight = fletcher_munson_weight(i);
            eq_gains[i] = (target_db - measured_db) * perceptual_weight * 0.7f;
            // 0.7 = conservative correction (avoid over-EQ)
        }
    }

private:
    float fletcher_munson_weight(uint32_t band_idx) const noexcept {
        // Simplified Fletcher-Munson curve approximation
        // Lower weights for extreme frequencies where ear is less sensitive
        if (band_idx < 10) return 0.5f;      // Very low freqs
        if (band_idx > 240) return 0.6f;     // Very high freqs
        return 1.0f;                          // Midrange (most sensitive)
    }
    
    DeviceFingerprint current_fp_{};
};

} // namespace ivanna::consistency
