/**
 * omega_daemon.cpp — Root AI Daemon para Ω_in
 * ==============================================
 * Proceso separado iniciado por Magisk service.sh.
 * Lee audio del ring buffer compartido, ejecuta inferencia Ω_in,
 * escribe resultado al ring buffer de salida.
 *
 * Hilos:
 *   1. Main thread: bucle de procesamiento de audio (big core affinity)
 *   2. Socket thread: escucha comandos de la APK
 *   3. Thermal thread: monitorea temperatura
 *
 * CERO malloc/free en el bucle de audio. Toda la memoria pre-allocada.
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>
#include <time.h>
#include <pthread.h>
#include <sched.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <fcntl.h>
#include <android/log.h>

#include <atomic>

#include "omega_shared.h"

#define LOG_TAG "OmegaDaemon"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════
// ESTADO GLOBAL (pre-allocado, sin malloc en hot path)
// ═══════════════════════════════════════════════════════════════
static std::atomic<bool> g_running{true};
static OmegaSharedData*  g_shared = nullptr;
static int               g_shm_fd = -1;
static int               g_server_fd = -1;

// Buffer de procesamiento pre-allocado (CERO malloc en bucle)static float g_process_buf[OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];
static float g_output_buf[OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];

// ═══════════════════════════════════════════════════════════════
// SEÑALES (shutdown graceful)
// ═══════════════════════════════════════════════════════════════
static void signal_handler(int sig)
{
    (void)sig;
    g_running.store(false, std::memory_order_release);
    LOGI("Shutdown signal received");
}

// ═══════════════════════════════════════════════════════════════
// THERMAL MONITOR (lee temperatura del SoC)
// ═══════════════════════════════════════════════════════════════
static float read_soc_temperature(void)
{
    FILE* fp = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if (fp == NULL) {
        return 0.0f;
    }
    int temp_millideg = 0;
    if (fscanf(fp, "%d", &temp_millideg) != 1) {
        fclose(fp);
        return 0.0f;
    }
    fclose(fp);
    return (float)temp_millideg / 1000.0f;
}

// ═══════════════════════════════════════════════════════════════
// THREAD: MONITOREO TÉRMICO
// ═══════════════════════════════════════════════════════════════
static void* thermal_thread_func(void* arg)
{
    (void)arg;
    LOGI("Thermal monitor thread started");

    while (g_running.load(std::memory_order_acquire)) {
        float temp = read_soc_temperature();

        if (g_shared != nullptr) {
            g_shared->current_temperature.store(temp, std::memory_order_relaxed);

            // Si supera 42°C, activar throttling
            if (temp > 42.0f) {
                bool was_throttling = g_shared->thermal_throttling.load(std::memory_order_relaxed);
                if (!was_throttling) {
                    LOGW("Thermal throttling ON: %.1f°C", temp);                }
                g_shared->thermal_throttling.store(true, std::memory_order_release);

                // Reducir complejidad: bajar proyecciones SWD
                int current_proj = g_shared->swd_projections.load(std::memory_order_relaxed);
                if (current_proj > 16) {
                    g_shared->swd_projections.store(current_proj / 2, std::memory_order_relaxed);
                }
            } else if (temp < 38.0f) {
                g_shared->thermal_throttling.store(false, std::memory_order_release);
            }
        }

        // Dormir 1 segundo entre lecturas
        struct timespec ts;
        ts.tv_sec = 1;
        ts.tv_nsec = 0;
        nanosleep(&ts, NULL);
    }

    LOGI("Thermal monitor thread stopped");
    return NULL;
}

// ═══════════════════════════════════════════════════════════════
// THREAD: SOCKET DE CONTROL (APK ↔ Daemon)
// ═══════════════════════════════════════════════════════════════
static void* socket_thread_func(void* arg)
{
    (void)arg;
    LOGI("Socket thread started");

    // Crear socket Unix Domain
    g_server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (g_server_fd < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return NULL;
    }

    // Borrar socket anterior si existe
    unlink(OMEGA_SOCKET_PATH);

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, OMEGA_SOCKET_PATH, sizeof(addr.sun_path) - 1);

    if (bind(g_server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to bind socket: %s", strerror(errno));
        close(g_server_fd);        g_server_fd = -1;
        return NULL;
    }

    chmod(OMEGA_SOCKET_PATH, 0666);

    if (listen(g_server_fd, 5) < 0) {
        LOGE("Failed to listen: %s", strerror(errno));
        close(g_server_fd);
        g_server_fd = -1;
        return NULL;
    }

    LOGI("Socket listening on %s", OMEGA_SOCKET_PATH);

    char recv_buf[1024];

    while (g_running.load(std::memory_order_acquire)) {
        // Accept con timeout (usar select para no bloquear indefinidamente)
        fd_set read_fds;
        FD_ZERO(&read_fds);
        FD_SET(g_server_fd, &read_fds);

        struct timeval tv;
        tv.tv_sec = 1;
        tv.tv_usec = 0;

        int sel = select(g_server_fd + 1, &read_fds, NULL, NULL, &tv);
        if (sel <= 0) {
            continue;
        }

        int client_fd = accept(g_server_fd, NULL, NULL);
        if (client_fd < 0) {
            continue;
        }

        // Leer comando
        ssize_t bytes_read = read(client_fd, recv_buf, sizeof(recv_buf) - 1);
        if (bytes_read > 0 && g_shared != nullptr) {
            recv_buf[bytes_read] = '\0';
            LOGI("Received command: %s", recv_buf);

            // Parsear comandos simples (formato: "key=value")
            if (strncmp(recv_buf, "bypass=", 7) == 0) {
                int val = atoi(recv_buf + 7);
                g_shared->bypass_mode.store(val != 0, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "intensity=", 10) == 0) {
                float val = (float)atof(recv_buf + 10);                g_shared->intensity.store(val, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "preset=", 7) == 0) {
                int val = atoi(recv_buf + 7);
                g_shared->preset_id.store(val, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "swd_proj=", 9) == 0) {
                int val = atoi(recv_buf + 9);
                g_shared->swd_projections.store(val, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "phase=", 6) == 0) {
                float val = (float)atof(recv_buf + 6);
                g_shared->phase_coherence.store(val, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "collapse=", 9) == 0) {
                float val = (float)atof(recv_buf + 9);
                g_shared->collapse_strength.store(val, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "vocoder=", 8) == 0) {
                float val = (float)atof(recv_buf + 8);
                g_shared->vocoder_mix.store(val, std::memory_order_release);
            }
            else if (strncmp(recv_buf, "status", 6) == 0) {
                // Responder con telemetría
                char resp[256];
                snprintf(resp, sizeof(resp),
                         "temp=%.1f;lat=%.2f;npu=%d;throttle=%d;blocks=%d;dropped=%d",
                         g_shared->current_temperature.load(std::memory_order_relaxed),
                         g_shared->current_latency_ms.load(std::memory_order_relaxed),
                         g_shared->npu_usage_percent.load(std::memory_order_relaxed),
                         g_shared->thermal_throttling.load(std::memory_order_relaxed) ? 1 : 0,
                         g_shared->processed_blocks.load(std::memory_order_relaxed),
                         g_shared->dropped_blocks.load(std::memory_order_relaxed));
                write(client_fd, resp, strlen(resp));
            }
        }

        close(client_fd);
    }

    close(g_server_fd);
    g_server_fd = -1;
    unlink(OMEGA_SOCKET_PATH);

    LOGI("Socket thread stopped");
    return NULL;
}

// ═══════════════════════════════════════════════════════════════
// PROCESAMIENTO DE AUDIO (placeholder para Ω_in)// ═══════════════════════════════════════════════════════════════
// TODO: Reemplazar con inferencia ExecuTorch real
// Por ahora: aplica ganancia basada en intensity (prueba de concepto)
static void process_omega_block(const float* input,
                                float* output,
                                size_t frames,
                                size_t channels,
                                float intensity)
{
    // Placeholder: mix entre audio original y procesado
    // En producción: STFT → Ω_SWD → Ω_Fase → Ω_Colapso → iSTFT
    size_t total_samples = frames * channels;
    for (size_t i = 0; i < total_samples; ++i) {
        // Ganancia suave basada en intensity (0.0 = bypass, 1.0 = full effect)
        float sample = input[i];
        // Placeholder effect: soft saturation
        float processed = sample * (1.0f + 0.3f * intensity * (1.0f - sample * sample));
        // Mix
        output[i] = sample * (1.0f - intensity) + processed * intensity;
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN
// ═══════════════════════════════════════════════════════════════
int main(int argc, char* argv[])
{
    (void)argc;
    (void)argv;

    LOGI("Ω_in Daemon starting...");

    // Instalar handler de señales
    signal(SIGTERM, signal_handler);
    signal(SIGINT, signal_handler);

    // Fijar afinidad de CPU a big cores (cores 6-7 en Snapdragon 7s Gen 2)
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(6, &cpuset);
    CPU_SET(7, &cpuset);
    if (sched_setaffinity(0, sizeof(cpuset), &cpuset) == 0) {
        LOGI("CPU affinity set to cores 6-7");
    } else {
        LOGW("Failed to set CPU affinity: %s", strerror(errno));
    }

    // Establecer prioridad de tiempo real
    struct sched_param param;
    memset(&param, 0, sizeof(param));    param.sched_priority = sched_get_priority_max(SCHED_FIFO) - 1;
    if (sched_setscheduler(0, SCHED_FIFO, &param) != 0) {
        LOGW("Failed to set SCHED_FIFO: %s", strerror(errno));
    }

    // Crear shared memory
    g_shm_fd = shm_open("/omega_shared", O_CREAT | O_RDWR, 0666);
    if (g_shm_fd < 0) {
        // Fallback: usar archivo en /data/omega/
        mkdir("/data/omega", 0755);
        g_shm_fd = open(OMEGA_SHM_PATH, O_CREAT | O_RDWR, 0666);
    }

    if (g_shm_fd < 0) {
        LOGE("Failed to create shared memory: %s", strerror(errno));
        return 1;
    }

    // Tamaño del shared memory
    if (ftruncate(g_shm_fd, sizeof(OmegaSharedData)) != 0) {
        LOGE("Failed to truncate shm: %s", strerror(errno));
        close(g_shm_fd);
        return 1;
    }

    void* shm_ptr = mmap(NULL, sizeof(OmegaSharedData),
                         PROT_READ | PROT_WRITE, MAP_SHARED,
                         g_shm_fd, 0);
    if (shm_ptr == MAP_FAILED) {
        LOGE("Failed to mmap shm: %s", strerror(errno));
        close(g_shm_fd);
        return 1;
    }

    g_shared = (OmegaSharedData*)shm_ptr;

    // Inicializar shared memory
    g_shared->daemon_ready.store(false, std::memory_order_release);
    g_shared->bypass_mode.store(true, std::memory_order_release);
    g_shared->intensity.store(0.5f, std::memory_order_release);
    g_shared->preset_id.store(0, std::memory_order_release);
    g_shared->swd_projections.store(64, std::memory_order_release);
    g_shared->phase_coherence.store(0.8f, std::memory_order_release);
    g_shared->collapse_strength.store(0.5f, std::memory_order_release);
    g_shared->vocoder_mix.store(0.0f, std::memory_order_release);
    g_shared->current_temperature.store(0.0f, std::memory_order_release);
    g_shared->current_latency_ms.store(0.0f, std::memory_order_release);
    g_shared->npu_usage_percent.store(0, std::memory_order_release);
    g_shared->thermal_throttling.store(false, std::memory_order_release);
    g_shared->processed_blocks.store(0, std::memory_order_release);    g_shared->dropped_blocks.store(0, std::memory_order_release);
    g_shared->in_write_pos.store(0, std::memory_order_release);
    g_shared->in_read_pos.store(0, std::memory_order_release);
    g_shared->out_write_pos.store(0, std::memory_order_release);
    g_shared->out_read_pos.store(0, std::memory_order_release);

    LOGI("Shared memory initialized (%zu bytes)", sizeof(OmegaSharedData));

    // Iniciar thread térmico
    pthread_t thermal_tid;
    pthread_create(&thermal_tid, NULL, thermal_thread_func, NULL);

    // Iniciar thread de socket
    pthread_t socket_tid;
    pthread_create(&socket_tid, NULL, socket_thread_func, NULL);

    // Marcar daemon como listo
    g_shared->daemon_ready.store(true, std::memory_order_release);
    g_shared->bypass_mode.store(false, std::memory_order_release);
    LOGI("Daemon ready, processing enabled");

    // ═══ BUCLE PRINCIPAL DE PROCESAMIENTO ═══
    struct timespec block_start;
    struct timespec block_end;

    while (g_running.load(std::memory_order_acquire)) {

        clock_gettime(CLOCK_MONOTONIC, &block_start);

        // Leer del ring buffer de entrada
        size_t total_in_slots = OMEGA_BUFFER_FRAMES * OMEGA_MAX_CHANNELS;
        size_t in_wpos = g_shared->in_write_pos.load(std::memory_order_acquire);
        size_t in_rpos = g_shared->in_read_pos.load(std::memory_order_relaxed);
        size_t in_available = (in_wpos - in_rpos + total_in_slots) % total_in_slots;

        size_t block_samples = OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;

        if (in_available >= block_samples) {
            // Leer bloque de entrada
            for (size_t i = 0; i < block_samples; ++i) {
                g_process_buf[i] = g_shared->input_buffer[(in_rpos + i) % total_in_slots];
            }
            g_shared->in_read_pos.store(
                (in_rpos + block_samples) % total_in_slots,
                std::memory_order_release);

            // Obtener parámetros actuales
            float intensity = g_shared->intensity.load(std::memory_order_acquire);

            // Procesar (placeholder Ω_in)            process_omega_block(g_process_buf, g_output_buf,
                                OMEGA_BLOCK_SIZE, OMEGA_MAX_CHANNELS,
                                intensity);

            // Escribir al ring buffer de salida
            size_t total_out_slots = OMEGA_BUFFER_FRAMES * OMEGA_MAX_CHANNELS;
            size_t out_wpos = g_shared->out_write_pos.load(std::memory_order_relaxed);
            size_t out_rpos = g_shared->out_read_pos.load(std::memory_order_acquire);
            size_t out_used = (out_wpos - out_rpos + total_out_slots) % total_out_slots;
            size_t out_space = total_out_slots - out_used - 1;

            size_t to_write = (block_samples < out_space) ? block_samples : out_space;
            for (size_t i = 0; i < to_write; ++i) {
                g_shared->output_buffer[(out_wpos + i) % total_out_slots] = g_output_buf[i];
            }
            g_shared->out_write_pos.store(
                (out_wpos + to_write) % total_out_slots,
                std::memory_order_release);

            g_shared->processed_blocks.fetch_add(1, std::memory_order_relaxed);
        } else {
            // No hay suficiente audio, dormir brevemente
            struct timespec sleep_ts;
            sleep_ts.tv_sec = 0;
            sleep_ts.tv_nsec = 1000000; // 1ms
            nanosleep(&sleep_ts, NULL);
        }

        // Medir latencia del bloque
        clock_gettime(CLOCK_MONOTONIC, &block_end);
        float block_latency_ms = (float)(block_end.tv_sec - block_start.tv_sec) * 1000.0f
                               + (float)(block_end.tv_nsec - block_start.tv_nsec) / 1000000.0f;
        g_shared->current_latency_ms.store(block_latency_ms, std::memory_order_relaxed);
    }

    // ═══ SHUTDOWN ═══
    LOGI("Shutting down...");
    g_shared->daemon_ready.store(false, std::memory_order_release);

    pthread_join(socket_tid, NULL);
    pthread_join(thermal_tid, NULL);

    munmap(g_shared, sizeof(OmegaSharedData));
    close(g_shm_fd);
    shm_unlink("/omega_shared");

    LOGI("Daemon stopped");
    return 0;
}
