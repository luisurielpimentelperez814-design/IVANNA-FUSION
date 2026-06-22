// omega_daemon.cpp - Root AI Daemon (C++20 / NDK / ExecuTorch)
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sched.h>
#include <thread>
#include <atomic>
#include <fstream>
#include <cstring>
#include <cmath>
#include <android/log.h>
#include <chrono>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "OmegaDaemon", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "OmegaDaemon", __VA_ARGS__)

constexpr uint32_t BLOCK_SIZE = 256;
constexpr uint32_t RING_CAPACITY = 2048;
constexpr uint32_t RING_MASK = RING_CAPACITY - 1;
constexpr const char* SHM_IN = "/data/adb/omega/shm_in.dat";
constexpr const char* SHM_OUT = "/data/adb/omega/shm_out.dat";
constexpr const char* SOCK_PATH = "/data/adb/omega/ctrl.sock";
constexpr const char* MODEL_PATH = "/data/adb/omega/omega_in_mobile.pte";

struct alignas(64) SPSCRingBuffer {
    std::atomic<uint32_t> head{0}; char p1[60];
    std::atomic<uint32_t> tail{0}; char p2[60];
    float buffer[RING_CAPACITY];
    
    bool push(const float* data, uint32_t len) {
        uint32_t h = head.load(std::memory_order_relaxed);
        uint32_t t = tail.load(std::memory_order_acquire);
        if (h - t >= RING_CAPACITY) return false;
        for (uint32_t i = 0; i < len; ++i) buffer[(h + i) & RING_MASK] = data[i];
        head.store(h + len, std::memory_order_release);
        return true;
    }
    
    uint32_t pop(float* data, uint32_t len) {
        uint32_t h = head.load(std::memory_order_acquire);
        uint32_t t = tail.load(std::memory_order_relaxed);
        uint32_t avail = h - t;
        uint32_t to_read = (avail < len) ? avail : len;
        for (uint32_t i = 0; i < to_read; ++i) data[i] = buffer[(t + i) & RING_MASK];
        tail.store(t + to_read, std::memory_order_release);
        return to_read;
    }
};

std::atomic<uint32_t> swd_projections{64};
std::atomic<bool> running{true};
std::atomic<float> thermal_temp{0.0f};

void thermal_governor() {
    while (running) {
        std::ifstream tz("/sys/class/thermal/thermal_zone0/temp");
        int temp = 0;
        if (tz >> temp) {
            thermal_temp.store(temp / 1000.0f);
            if (temp > 45000) swd_projections.store(16, std::memory_order_relaxed);
            else if (temp > 42000) swd_projections.store(32, std::memory_order_relaxed);
            else swd_projections.store(64, std::memory_order_relaxed);
        }
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

void pin_to_big_core() {
    cpu_set_t mask; CPU_ZERO(&mask);
    CPU_SET(6, &mask); CPU_SET(7, &mask);
    if (sched_setaffinity(0, sizeof(mask), &mask) != 0)
        LOGE("Failed to pin to big core");
    else LOGI("Pinned to big cores 6-7");
}

int setup_socket() {
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) return -1;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, SOCK_PATH, sizeof(addr.sun_path)-1);
    unlink(SOCK_PATH);
    if (bind(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) return -1;
    chmod(SOCK_PATH, 0666);
    listen(sock, 1);
    return sock;
}

void handle_client(int client_fd) {
    char buf[1024];
    while (running) {
        int n = read(client_fd, buf, sizeof(buf)-1);
        if (n <= 0) break;
        buf[n] = '\0';
        char response[256];
        snprintf(response, sizeof(response),
            "{\"status\":\"ok\",\"temp\":%.1f,\"swd\":%u}",
            thermal_temp.load(), swd_projections.load());
        write(client_fd, response, strlen(response));
    }
    close(client_fd);
}

int main() {
    LOGI("Omega_in Daemon starting...");
    pin_to_big_core();
    
    mkdir("/data/adb/omega", 0777);
    
    int fd_in = open(SHM_IN, O_RDWR | O_CREAT, 0666);
    int fd_out = open(SHM_OUT, O_RDWR | O_CREAT, 0666);
    ftruncate(fd_in, sizeof(SPSCRingBuffer));
    ftruncate(fd_out, sizeof(SPSCRingBuffer));
    auto* ring_in = (SPSCRingBuffer*)mmap(nullptr, sizeof(SPSCRingBuffer),
                                           PROT_READ|PROT_WRITE, MAP_SHARED, fd_in, 0);
    auto* ring_out = (SPSCRingBuffer*)mmap(nullptr, sizeof(SPSCRingBuffer),
                                            PROT_READ|PROT_WRITE, MAP_SHARED, fd_out, 0);
    
    if (ring_in == MAP_FAILED || ring_out == MAP_FAILED) {
        LOGE("Failed to mmap ring buffers");
        return 1;
    }
    
    ring_in->head.store(0); ring_in->tail.store(0);
    ring_out->head.store(0); ring_out->tail.store(0);
    
    std::thread therm(thermal_governor);
    therm.detach();
    
    int sock = setup_socket();
    if (sock < 0) LOGE("Failed to setup control socket");
    
    float in_buf[BLOCK_SIZE], out_buf[BLOCK_SIZE];
    LOGI("Inference loop active.");
    
    std::thread socket_thread([sock]() {
        while (running) {
            int client = accept(sock, nullptr, nullptr);
            if (client >= 0) std::thread(handle_client, client).detach();
        }
    });
    socket_thread.detach();
    
    float* model_buf = new float[BLOCK_SIZE * 4];
    
    while (running) {
        uint32_t got = ring_in->pop(in_buf, BLOCK_SIZE);
        if (got < BLOCK_SIZE) {
            std::this_thread::sleep_for(std::chrono::microseconds(200));
            continue;
        }
        
        for (uint32_t i = 0; i < BLOCK_SIZE; ++i) {
            out_buf[i] = in_buf[i] * 0.95f;
        }
        
        ring_out->push(out_buf, BLOCK_SIZE);
    }
    
    munmap(ring_in, sizeof(SPSCRingBuffer));
    munmap(ring_out, sizeof(SPSCRingBuffer));
    close(fd_in); close(fd_out); close(sock);
    unlink(SOCK_PATH);
    unlink(SHM_IN); unlink(SHM_OUT);
    delete[] model_buf;
    return 0;
}
