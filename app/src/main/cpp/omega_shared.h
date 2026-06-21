#ifndef OMEGA_SHARED_H
#define OMEGA_SHARED_H

#include <atomic>
#include <cstdint>
#include <cstring>

// Constantes del motor
#define OMEGA_BLOCK_SIZE       512
#define OMEGA_SAMPLE_RATE      48000
#define OMEGA_MAX_CHANNELS     2
#define OMEGA_BUFFER_SLOTS     16   // debe ser potencia de 2 (ver wrap-around abajo)

// ─────────────────────────────────────────────────────────────────────────────
// Ring buffer SPSC (Single Producer Single Consumer) lock-free.
//
// La versión anterior de OmegaSharedState comparaba write_pos == read_pos
// para decidir "no hay datos", lo cual es ambiguo: con índices que se
// envuelven (% OMEGA_BUFFER_SLOTS) esa misma condición también significa
// "buffer lleno" -> bajo carga sostenida el daemon podía interpretar
// "lleno" como "vacío" y perder bloques de audio silenciosamente.
//
// Esta versión sigue el patrón clásico de ring buffer SPSC: el productor
// (omega_effect.cpp, en el hilo de AudioFlinger) solo escribe head_; el
// consumidor (omega_daemon.cpp) solo escribe tail_. Ninguno de los dos
// escribe el índice del otro -> sin necesidad de locks ni CAS. La
// distinción lleno/vacío se hace con un slot de margen (capacity = N-1
// usables), no comparando los índices crudos.
// ─────────────────────────────────────────────────────────────────────────────
template <int SLOTS>
class OmegaRingBuffer {
    static_assert((SLOTS & (SLOTS - 1)) == 0, "SLOTS debe ser potencia de 2");

public:
    // Productor (omega_effect.cpp / hilo de AudioFlinger). No debe bloquear.
    // Devuelve false si el buffer está lleno (el llamador debe hacer bypass,
    // nunca esperar).
    bool tryPush(const float* block, int samplesPerBlock, float* storage) {
        uint32_t head = head_.load(std::memory_order_relaxed);
        uint32_t nextHead = (head + 1) & (SLOTS - 1);
        uint32_t tail = tail_.load(std::memory_order_acquire);
        if (nextHead == tail) {
            return false;  // lleno: el consumidor no ha drenado a tiempo
        }
        std::memcpy(&storage[head * samplesPerBlock], block,
                    (size_t)samplesPerBlock * sizeof(float));
        head_.store(nextHead, std::memory_order_release);
        return true;
    }

    // Consumidor (omega_daemon.cpp). No debe bloquear.
    // Devuelve false si el buffer está vacío.
    bool tryPop(float* block, int samplesPerBlock, const float* storage) {
        uint32_t tail = tail_.load(std::memory_order_relaxed);
        uint32_t head = head_.load(std::memory_order_acquire);
        if (tail == head) {
            return false;  // vacío
        }
        std::memcpy(block, &storage[tail * samplesPerBlock],
                    (size_t)samplesPerBlock * sizeof(float));
        tail_.store((tail + 1) & (SLOTS - 1), std::memory_order_release);
        return true;
    }

    uint32_t size() const {
        uint32_t head = head_.load(std::memory_order_acquire);
        uint32_t tail = tail_.load(std::memory_order_acquire);
        return (head - tail) & (SLOTS - 1);
    }

private:
    std::atomic<uint32_t> head_{0};  // escrito solo por el productor
    std::atomic<uint32_t> tail_{0};  // escrito solo por el consumidor
};

// Estructura de estado compartido (compatible con Android NDK, mmap-friendly:
// sin punteros, sin vtables, todo offset relativo dentro de la propia
// estructura para que productor y consumidor en procesos DISTINTOS la vean
// idéntica tras mapear el mismo archivo).
struct OmegaSharedState {
    std::atomic<float>   intensity;
    std::atomic<bool>    is_processing;
    std::atomic<bool>    bypass_enabled;
    std::atomic<float>   phase_coherence;
    std::atomic<float>   collapse_strength;
    std::atomic<float>   vocoder_mix;
    std::atomic<float>   current_temperature;
    std::atomic<float>   current_latency_ms;

    // Buffers circulares para audio (se conservan para compatibilidad con
    // código existente que los referencia directamente; el acceso nuevo
    // debe pasar por ring_in_/ring_out_ + tryPush/tryPop, no por
    // write_pos/read_pos crudos, que tenían la ambigüedad lleno/vacío).
    float input_buffer[OMEGA_BUFFER_SLOTS][OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];
    float output_buffer[OMEGA_BUFFER_SLOTS][OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];

    std::atomic<uint32_t> write_pos;   // conservado, ya no es la fuente de verdad
    std::atomic<uint32_t> read_pos;    // conservado, ya no es la fuente de verdad

    // Ring buffers SPSC reales: ring_in_ lo escribe omega_effect (productor)
    // y lo lee omega_daemon (consumidor); ring_out_ es al revés.
    OmegaRingBuffer<OMEGA_BUFFER_SLOTS> ring_in;
    OmegaRingBuffer<OMEGA_BUFFER_SLOTS> ring_out;

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
