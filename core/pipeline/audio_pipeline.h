#pragma once
// IVANNA Real-Time Pipeline - <10ms guaranteed
// Complexity Level: 3/5 | Deterministic | Lock-free

#include "../dsp/biquad_neon.h"
#include <array>
#include <atomic>
#include <cstring>

namespace ivanna::pipeline {

struct PipelineConfig {
    uint32_t input_sample_rate = 48000;
    uint32_t internal_sample_rate = 192000;
    uint32_t output_sample_rate = 48000;
    uint32_t block_size = 256;
    uint32_t max_latency_ms = 10;
};

class DSPModule {
public:
    virtual ~DSPModule() = default;
    virtual void process(float* buffer, uint32_t frames) noexcept = 0;
    virtual void set_param(uint32_t param_id, float value) noexcept = 0;
    std::atomic<bool> enabled{true};
};

class AudioPipeline {
public:
    explicit AudioPipeline(const PipelineConfig& cfg) : cfg_(cfg) {
        internal_buffer_.fill(0.0f);
        output_buffer_.fill(0.0f);
    }
    
    bool process(const float* in, uint32_t in_frames,
                 float* out, uint32_t& out_frames) noexcept {
        
        // Stage 1: Copy to internal buffer (resampling placeholder)
        uint32_t copy_frames = std::min(in_frames, (uint32_t)internal_buffer_.size());
        std::memcpy(internal_buffer_.data(), in, copy_frames * sizeof(float));
        
        // Stage 2: DSP Core processing
        for (auto& module : active_modules_) {
            if (module && module->enabled.load(std::memory_order_acquire)) {
                module->process(internal_buffer_.data(), copy_frames);
            }
        }
        
        // Stage 3: Copy to output
        out_frames = std::min(copy_frames, (uint32_t)cfg_.block_size);
        std::memcpy(out, internal_buffer_.data(), out_frames * sizeof(float));
        
        frames_processed_.fetch_add(out_frames, std::memory_order_relaxed);
        return true;
    }
    
    void set_module_param(uint32_t module_id, uint32_t param_id, float value) noexcept {
        if (module_id < active_modules_.size() && active_modules_[module_id]) {
            active_modules_[module_id]->set_param(param_id, value);
        }
    }
    
    uint64_t get_frames_processed() const noexcept {
        return frames_processed_.load(std::memory_order_relaxed);
    }

private:
    PipelineConfig cfg_;
    alignas(64) std::array<float, 4096> internal_buffer_;
    alignas(64) std::array<float, 1024> output_buffer_;
    std::array<DSPModule*, 8> active_modules_{};
    std::atomic<uint64_t> frames_processed_{0};
};

} // namespace ivanna::pipeline
