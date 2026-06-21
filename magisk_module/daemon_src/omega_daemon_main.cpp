/*
 * IVANNA-FUSION / Ω_in
 * omega_daemon_main.cpp — Ejecutable standalone para módulo Magisk
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <atomic>
#include <thread>
#include <chrono>
#include <signal.h>

#include "omega_shared.h"

#define LOG_TAG "OmegaDaemon"
#define SHM_PATH "/data/local/tmp/omega_shared_mem"
#define SOCKET_NAME "omega_daemon_socket"
#define THERMAL_LIMIT_C 42.0f

static OmegaSharedState* g_shared = nullptr;
static int g_shm_fd = -1;
static std::atomic<bool> g_running{false};
static std::thread g_process_thread;
static std::thread g_socket_thread;
static int g_socket_fd = -1;
static float g_process_buf[OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];
static std::atomic<int> g_complexity_level{0};

static void signal_handler(int sig) {
    g_running.store(false);
}

static float read_battery_temperature() {
    FILE* f = fopen("/sys/class/power_supply/battery/temp", "r");
    if (!f) return g_shared ? g_shared->current_temperature.load() : 35.0f;
    int raw = 0;
    if (fscanf(f, "%d", &raw) != 1) { fclose(f); return 35.0f; }
    fclose(f);
    return raw / 10.0f;
}

static void update_thermal_state() {
    float temp = read_battery_temperature();
    if (g_shared) g_shared->current_temperature.store(temp);
    
    int level = 0;
    if (temp >= THERMAL_LIMIT_C + 5.0f) level = 2;
    else if (temp >= THERMAL_LIMIT_C) level = 1;
    g_complexity_level.store(level);
}

static bool init_shared_memory() {
    g_shm_fd = open(SHM_PATH, O_CREAT | O_RDWR, 0666);
    if (g_shm_fd < 0) {
        fprintf(stderr, "Error opening shared memory: %s\n", SHM_PATH);
        return false;
    }
    if (ftruncate(g_shm_fd, sizeof(OmegaSharedState)) < 0) {
        fprintf(stderr, "Error resizing shared memory\n");
        close(g_shm_fd);
        return false;
    }
    void* mapped = mmap(nullptr, sizeof(OmegaSharedState),
                        PROT_READ | PROT_WRITE, MAP_SHARED, g_shm_fd, 0);
    if (mapped == MAP_FAILED) {
        fprintf(stderr, "Error mapping shared memory\n");
        close(g_shm_fd);
        return false;
    }
    g_shared = static_cast<OmegaSharedState*>(mapped);
    new (g_shared) OmegaSharedState();
    printf("Shared memory initialized\n");
    return true;
}

static void cleanup_shared_memory() {
    if (g_shared) {
        munmap(g_shared, sizeof(OmegaSharedState));
        g_shared = nullptr;
    }
    if (g_shm_fd >= 0) {
        close(g_shm_fd);
        g_shm_fd = -1;
    }
}

static void run_inference(const float* input, float* output, int n_samples) {
    int level = g_complexity_level.load();
    if (level >= 2) {
        memcpy(output, input, n_samples * sizeof(float));
        return;
    }
    memcpy(output, input, n_samples * sizeof(float));
}

static void process_audio_thread() {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(6, &cpuset);
    CPU_SET(7, &cpuset);
    sched_setaffinity(0, sizeof(cpuset), &cpuset);
    
    printf("Audio processing thread started\n");
    const int samples = OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;
    int thermal_counter = 0;
    
    while (g_running.load()) {
        if (!g_shared->is_processing.load() || g_shared->bypass_enabled.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }
        
        if (++thermal_counter >= 200) {
            thermal_counter = 0;
            update_thermal_state();
        }
        
        bool got_block = g_shared->ring_in.tryPop(
            g_process_buf, samples, &g_shared->input_buffer[0][0]);
        
        if (!got_block) {
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            continue;
        }
        
        auto t0 = std::chrono::steady_clock::now();
        run_inference(g_process_buf, g_process_buf, samples);
        auto t1 = std::chrono::steady_clock::now();
        
        float latency_ms = std::chrono::duration<float, std::milli>(t1 - t0).count();
        g_shared->current_latency_ms.store(latency_ms);
        
        g_shared->ring_out.tryPush(g_process_buf, samples,
                                   &g_shared->output_buffer[0][0]);
    }
    
    printf("Audio processing thread stopped\n");
}

static void handle_command(const std::string& cmd, int client_fd) {
    auto starts = [&](const char* prefix) {
        return cmd.rfind(prefix, 0) == 0;
    };
    
    if (starts("SET_PROCESSING:")) {
        bool on = cmd.back() == '1';
        if (g_shared) g_shared->is_processing.store(on);
    } else if (starts("SET_INTENSITY:")) {
        float v = strtof(cmd.c_str() + strlen("SET_INTENSITY:"), nullptr);
        if (g_shared) g_shared->intensity.store(v);
    } else if (starts("SET_VOCODER_MIX:")) {
        float v = strtof(cmd.c_str() + strlen("SET_VOCODER_MIX:"), nullptr);
        if (g_shared) g_shared->vocoder_mix.store(v);
    } else if (starts("SET_BYPASS:")) {
        bool on = cmd.back() == '1';
        if (g_shared) g_shared->bypass_enabled.store(on);
    } else if (starts("SET_THERMAL_THROTTLE:")) {
        bool on = cmd.back() == '1';
        if (on) g_complexity_level.store(2);
    } else if (starts("RESET_DEFAULTS")) {
        if (g_shared) {
            g_shared->intensity.store(0.8f);
            g_shared->vocoder_mix.store(0.8f);
            g_shared->bypass_enabled.store(false);
        }
    } else if (starts("SET_PRESET:")) {
        std::string preset = cmd.substr(strlen("SET_PRESET:"));
        printf("Preset received: %s\n", preset.c_str());
    } else if (starts("GET_TELEMETRY")) {
        float temp = g_shared ? g_shared->current_temperature.load() : 0.0f;
        float lat = g_shared ? g_shared->current_latency_ms.load() : 0.0f;
        int level = g_complexity_level.load();
        char buf[160];
        snprintf(buf, sizeof(buf),
                 "{\"temp\":%.1f,\"npu\":0.0,\"latency\":%.2f,\"complexity_level\":%d}\n",
                 temp, lat, level);
        write(client_fd, buf, strlen(buf));
        return;
    }
    
    const char* ack = "OK\n";
    write(client_fd, ack, strlen(ack));
}

static void socket_server_thread() {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        fprintf(stderr, "socket() failed\n");
        return;
    }
    
    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, SOCKET_NAME, sizeof(addr.sun_path) - 2);
    socklen_t addrlen = sizeof(addr.sun_family) + 1 + strlen(SOCKET_NAME);
    
    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), addrlen) < 0) {
        fprintf(stderr, "bind() failed on socket '%s'\n", SOCKET_NAME);
        close(fd);
        return;
    }
    if (listen(fd, 4) < 0) {
        fprintf(stderr, "listen() failed\n");
        close(fd);
        return;
    }
    
    g_socket_fd = fd;
    printf("Unix Domain Socket listening on '%s'\n", SOCKET_NAME);
    
    while (g_running.load()) {
        int client = accept(fd, nullptr, nullptr);
        if (client < 0) {
            if (!g_running.load()) break;
            continue;
        }
        
        char line[256];
        while (g_running.load()) {
            ssize_t n = read(client, line, sizeof(line) - 1);
            if (n <= 0) break;
            line[n] = '\0';
            std::string cmd(line);
            while (!cmd.empty() && (cmd.back() == '\n' || cmd.back() == '\r')) cmd.pop_back();
            handle_command(cmd, client);
        }
        close(client);
    }
    
    close(fd);
    g_socket_fd = -1;
}

int main(int argc, char** argv) {
    printf("Ω_in Edge AI Audio Engine Daemon starting...\n");
    
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    if (!init_shared_memory()) {
        return 1;
    }
    
    g_running.store(true);
    g_process_thread = std::thread(process_audio_thread);
    g_socket_thread = std::thread(socket_server_thread);
    
    printf("Daemon running (PID: %d)\n", getpid());
    
    while (g_running.load()) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    
    printf("Shutting down...\n");
    
    if (g_socket_fd >= 0) {
        shutdown(g_socket_fd, SHUT_RDWR);
    }
    
    if (g_process_thread.joinable()) g_process_thread.join();
    if (g_socket_thread.joinable()) g_socket_thread.join();
    
    cleanup_shared_memory();
    
    printf("Daemon stopped\n");
    return 0;
}
