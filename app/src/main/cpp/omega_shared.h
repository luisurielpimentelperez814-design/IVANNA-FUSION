#ifndef OMEGA_SHARED_H
#define OMEGA_SHARED_H

#include <atomic>
#include <cstdint>
#include <cstring>

// Constantes del motor
#define OMEGA_BLOCK_SIZE       512
#define OMEGA_SAMPLE_RATE      48000
#define OMEGA_MAX_CHANNELS     2
#define OMEGA_BUFFER_SLOTS     16

// Ring buffer lock-free para audio
template<typename T, int Capacity>
class LockFreeRing {
public:
    bool tryPush(const T* data, int count, T* buffer) {
        int head = m_head.load(std::memory_order_relaxed);
        int next_head = (head + 1) % Capacity;
        if (next_head == m_tail.load(std::memory_order_acquire)) return false;
        
        int offset = head * count;
        std::memcpy(buffer + offset, data, count * sizeof(T));
        m_head.store(next_head, std::memory_order_release);
        return true;
    }
    
    bool tryPop(T* data, int count, T* buffer) {
        int tail = m_tail.load(std::memory_order_relaxed);
        if (tail == m_head.load(std::memory_order_acquire)) return false;
        
        int offset = tail * count;
        std::memcpy(data, buffer + offset, count * sizeof(T));
        int next_tail = (tail + 1) % Capacity;
        m_tail.store(next_tail, std::memory_order_release);
        return true;
    }

private:
    std::atomic<int> m_head{0};
    std::atomic<int> m_tail{0};
};

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

    // Ring buffers para audio
    LockFreeRing<float, OMEGA_BUFFER_SLOTS> ring_in;
    LockFreeRing<float, OMEGA_BUFFER_SLOTS> ring_out;

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
