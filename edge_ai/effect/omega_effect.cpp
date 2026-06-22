// omega_effect.cpp - AudioFlinger Plugin (C++20 / NDK)
#include <hardware/audio_effect.h>
#include <atomic>
#include <cstring>
#include <cmath>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "OmegaEffect", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "OmegaEffect", __VA_ARGS__)

constexpr uint32_t BLOCK_SIZE = 256;
constexpr uint32_t RING_CAPACITY = 2048;
constexpr uint32_t RING_MASK = RING_CAPACITY - 1;

struct alignas(64) SPSCRingBuffer {
    std::atomic<uint32_t> head{0};
    char pad1[60];
    std::atomic<uint32_t> tail{0};
    char pad2[60];
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

constexpr const char* SHM_IN_PATH = "/data/adb/omega/shm_in.dat";
constexpr const char* SHM_OUT_PATH = "/data/adb/omega/shm_out.dat";

struct OmegaEffectContext {
    effect_handle_t handle;
    SPSCRingBuffer* ring_in;
    SPSCRingBuffer* ring_out;
    float stft_buf[BLOCK_SIZE];
    float istft_buf[BLOCK_SIZE];
    bool bypass;
    bool shm_ready;
};

alignas(32) static float hann_win[BLOCK_SIZE];
static bool win_init = false;

static void init_window() {
    if (win_init) return;
    for (uint32_t i = 0; i < BLOCK_SIZE; ++i)
        hann_win[i] = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / (BLOCK_SIZE - 1)));
    win_init = true;
}

static void attach_shm(OmegaEffectContext* ctx) {
    int fd_in = open(SHM_IN_PATH, O_RDWR);
    int fd_out = open(SHM_OUT_PATH, O_RDWR);
    if (fd_in < 0 || fd_out < 0) { ctx->shm_ready = false; return; }
    ctx->ring_in = (SPSCRingBuffer*)mmap(nullptr, sizeof(SPSCRingBuffer),
                                          PROT_READ|PROT_WRITE, MAP_SHARED, fd_in, 0);
    ctx->ring_out = (SPSCRingBuffer*)mmap(nullptr, sizeof(SPSCRingBuffer),
                                           PROT_READ|PROT_WRITE, MAP_SHARED, fd_out, 0);
    close(fd_in); close(fd_out);
    ctx->shm_ready = (ctx->ring_in != MAP_FAILED && ctx->ring_out != MAP_FAILED);
}

static int32_t omega_process(effect_handle_t self, audio_buffer_t* in, audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<OmegaEffectContext*>(self);
    if (!ctx || !in || !out) return -EINVAL;
    
    const int16_t* in_ptr = (const int16_t*)in->raw;
    int16_t* out_ptr = (int16_t*)out->raw;
    uint32_t frames = in->frameCount;
    
    if (!ctx->shm_ready) {
        memcpy(out_ptr, in_ptr, frames * sizeof(int16_t));
        out->frameCount = frames;
        return 0;
    }
    
    for (uint32_t i = 0; i < frames && i < BLOCK_SIZE; ++i) {
        ctx->stft_buf[i] = (in_ptr[i] / 32768.0f) * hann_win[i];
    }
    
    ctx->ring_in->push(ctx->stft_buf, frames);
    uint32_t got = ctx->ring_out->pop(ctx->istft_buf, frames);
    
    if (got < frames) {
        memcpy(out_ptr, in_ptr, frames * sizeof(int16_t));
    } else {
        for (uint32_t i = 0; i < frames; ++i) {
            float val = ctx->istft_buf[i] * hann_win[i] * 32767.0f;
            if (val > 32767.0f) val = 32767.0f;
            if (val < -32768.0f) val = -32768.0f;
            out_ptr[i] = (int16_t)val;
        }
    }
    
    out->frameCount = frames;
    return 0;
}

static int32_t omega_command(effect_handle_t self, uint32_t cmdCode, uint32_t cmdSize,
                             void* cmdData, uint32_t* replySize, void* replyData) {
    auto* ctx = reinterpret_cast<OmegaEffectContext*>(self);
    switch (cmdCode) {
        case EFFECT_CMD_INIT:
            init_window();
            attach_shm(ctx);
            if (replySize) *replySize = sizeof(int32_t);
            if (replyData) *(int32_t*)replyData = 0;
            return 0;
        case EFFECT_CMD_RELEASE:
            if (ctx->shm_ready) {
                munmap(ctx->ring_in, sizeof(SPSCRingBuffer));
                munmap(ctx->ring_out, sizeof(SPSCRingBuffer));
            }
            delete ctx;
            return 0;
        default:
            return -EINVAL;
    }
}

extern "C" {
    effect_library_t AUDIO_EFFECT_LIBRARY_INFO = {
        .tag = EFFECT_LIBRARY_TAG,
        .version = EFFECT_LIBRARY_API_VERSION,
        .name = "Omega Effect Library",
        .implementor = "IVANNA Fusion",
        .query_num_effects = [](uint32_t* num) { *num = 1; return 0; },
        .query_effect = [](uint32_t index, effect_descriptor_t* desc) { return 0; },
        .create_effect = [](const effect_uuid_t* uuid, int32_t sessionId, int32_t ioId,
                           effect_handle_t* out) {
            auto* ctx = new OmegaEffectContext();
            ctx->bypass = false;
            ctx->shm_ready = false;
            *out = (effect_handle_t)ctx;
            return 0;
        },
        .release_effect = [](effect_handle_t handle) { return 0; },
        .get_descriptor = [](const effect_uuid_t* uuid, effect_descriptor_t* desc) { return 0; }
    };
}
