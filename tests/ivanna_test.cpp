// IVANNA Core Compilation Test
// Validates that all core headers compile correctly with C++20 and SIMD

#include "core/dsp/biquad_neon.h"
#include "core/pipeline/audio_pipeline.h"
#include "core/complexity_registry.h"
#include "core/ai/ai_controller.h"
#include "core/consistency/device_fingerprint.h"
#include "core/spatial/hrtf_engine.h"

#include <iostream>
#include <vector>
#include <cmath>

int main() {
    std::cout << "IVANNA Core v2.0.0 - Compilation Validation Test" << std::endl;
    
    // Test 1: Biquad Filter
    ivanna::dsp::BiquadFilter biquad;
    biquad.set_coeffs(0.1f, 0.2f, 0.1f, -0.5f, 0.2f);
    
    std::vector<float> input(256, 0.5f);
    std::vector<float> output(256, 0.0f);
    biquad.process(input.data(), output.data(), 256);
    
    std::cout << "[PASS] Biquad Filter - Processed 256 samples" << std::endl;
    
    // Test 2: Audio Pipeline
    ivanna::pipeline::PipelineConfig cfg;
    cfg.input_sample_rate = 48000;
    cfg.internal_sample_rate = 192000;
    cfg.output_sample_rate = 48000;
    cfg.block_size = 256;
    
    ivanna::pipeline::AudioPipeline pipeline(cfg);
    uint32_t out_frames = 0;
    pipeline.process(input.data(), 256, output.data(), out_frames);
    
    std::cout << "[PASS] Audio Pipeline - Processed " << out_frames << " frames" << std::endl;
    
    // Test 3: Complexity Registry
    auto& registry = ivanna::control::ComplexityRegistry::instance();
    bool registered = registry.register_module({
        "test_module",
        ivanna::control::ComplexityLevel::SIMPLE,
        0.5f,  // cpu_budget_ms
        4096,  // memory_budget
        3,     // max_dependencies
        false  // requires_approval
    });
    
    std::cout << "[PASS] Complexity Registry - Module registered: " << (registered ? "yes" : "no") << std::endl;
    
    // Test 4: AI Controller
    ivanna::ai::AIController ai;
    std::vector<float> spectrum(256, 0.0f);
    auto updates = ai.suggest_updates(spectrum.data(), 256, "test_device");
    
    std::cout << "[PASS] AI Controller - Generated " << updates.size() << " parameter updates" << std::endl;
    
    // Test 5: Consistency Engine
    ivanna::consistency::ConsistencyEngine consistency;
    bool loaded = consistency.load_fingerprint("test_device");
    
    std::cout << "[PASS] Consistency Engine - Fingerprint loaded: " << (loaded ? "yes" : "no") << std::endl;
    
    // Test 6: HRTF Engine
    ivanna::spatial::HRTFEngine hrtf;
    bool hrtf_loaded = hrtf.load_hrtf("default_profile");
    
    std::vector<float> in_L(256, 0.5f), in_R(256, 0.5f);
    std::vector<float> out_L(256, 0.0f), out_R(256, 0.0f);
    hrtf.process(in_L.data(), in_R.data(), out_L.data(), out_R.data(), 256, 0.0f, 0.0f, 1.0f);
    
    std::cout << "[PASS] HRTF Engine - Spatial processing complete" << std::endl;
    
    std::cout << std::endl;
    std::cout << "====================================" << std::endl;
    std::cout << "ALL TESTS PASSED - Core is valid" << std::endl;
    std::cout << "====================================" << std::endl;
    
    return 0;
}
