#ifndef OMEGA_SHARED_H
#define OMEGA_SHARED_H

#include <stdatomic.h>
#include <stdint.h>
#include <stddef.h>

// ═══════════════════════════════════════════════════════════════
// CONSTANTES COMPARTIDAS (plugin ↔ daemon ↔ APK)
// ═══════════════════════════════════════════════════════════════
#define OMEGA_SHM_PATH         "/dev/shm/omega_shared"
#define OMEGA_SOCKET_PATH      "/data/omega/control.sock"
#define OMEGA_BUFFER_FRAMES    4096
#define OMEGA_MAX_CHANNELS     2
#define OMEGA_BLOCK_SIZE       512

// ═══════════════════════════════════════════════════════════════
// ESTRUCTURA DE SHARED MEMORY (mmap entre plugin y daemon)
// ═══════════════════════════════════════════════════════════════
typedef struct {
    // Control flags (escritos por daemon, leídos por plugin)
    atomic_bool    daemon_ready;
    atomic_bool    bypass_mode;

    // Parámetros (escritos por APK via socket, leídos por daemon)
    atomic_float   intensity;
    atomic_int     preset_id;
    atomic_int     swd_projections;
    atomic_float   phase_coherence;
    atomic_float   collapse_strength;
    atomic_float   vocoder_mix;

    // Telemetría (escrita por daemon, leída por APK via socket)
    atomic_float   current_temperature;
    atomic_float   current_latency_ms;
    atomic_int     npu_usage_percent;
    atomic_bool    thermal_throttling;
    atomic_int     processed_blocks;
    atomic_int     dropped_blocks;

    // ═══ SPSC Ring Buffer: INPUT (plugin → daemon) ═══
    atomic_size_t  in_write_pos;
    atomic_size_t  in_read_pos;
    float          input_buffer[OMEGA_BUFFER_FRAMES * OMEGA_MAX_CHANNELS];

    // ═══ SPSC Ring Buffer: OUTPUT (daemon → plugin) ═══
    atomic_size_t  out_write_pos;
    atomic_size_t  out_read_pos;
    float          output_buffer[OMEGA_BUFFER_FRAMES * OMEGA_MAX_CHANNELS];

} OmegaSharedData;

#endif // OMEGA_SHARED_H
