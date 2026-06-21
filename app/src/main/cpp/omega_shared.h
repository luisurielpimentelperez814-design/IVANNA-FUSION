#ifndef OMEGA_SHARED_H
#define OMEGA_SHARED_H

#include <atomic>
#include <cstdint>

// Constantes del motor
#define OMEGA_BLOCK_SIZE       512
#define OMEGA_SAMPLE_RATE      48000
#define OMEGA_MAX_CHANNELS     2
#define OMEGA_BUFFER_SLOTS     16

// Estructura de estado compartido (compatible con Android NDK)
struct OmegaSharedState {
    std::atomic<float>   intensity;
    std::atomic<bool>    is_processing;
    std::atomic<bool>    bypass_enabled;
    std::atomic<float>   phase_coherence;
    std::atomic<float>   collapse_strength;
    std::atomic<float>   vocoder_mix;
    std::atomic<float>   current_temperature;
    std::atomic<float>   current_latency_ms;
    
    // Buffers circulares para audio
    float input_buffer[OMEGA_BUFFER_SLOTS][OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];
    float output_buffer[OMEGA_BUFFER_SLOTS][OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];
    
    std::atomic<uint32_t> write_pos;
    std::atomic<uint32_t> read_pos;
    
    OmegaSharedState() : 
        intensity(0.8f),
        is_processing(false),
        bypass_enabled(false),
        phase_coherence(1.0f),
        collapse_strength(0.5f),
        vocoder_mix(0.8f),
        current_temperature(35.0f),
        current_latency_ms(0.0f),
        write_pos(0),
        read_pos(0) {}
};

#endif // OMEGA_SHARED_H
