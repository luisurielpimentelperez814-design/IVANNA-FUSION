/**
 * omega_effect.cpp — Audio Effect Plugin para AudioFlinger
 * =========================================================
 * SOLO mueve datos entre AudioFlinger y shared memory.
 * NUNCA ejecuta inferencia de IA en el callback de audio.
 * Si el daemon no está listo o el buffer de salida está vacío,
 * pasa el audio limpio (bypass) para evitar silencios.
 */

#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <android/log.h>

#include <hardware/audio_effect.h>

#include "omega_shared.h"

#define LOG_TAG "OmegaEffect"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════
// UUID DEL EFECTO
// ═══════════════════════════════════════════════════════════════
static const effect_uuid_t OMEGA_EFFECT_UUID = {
    { 0xd4, 0xc3, 0xb2, 0xa1, 0xf6, 0xe5, 0x90, 0x78,
      0xab, 0xcd, 0xef, 0x12, 0x34, 0x56, 0x78, 0x90 }
};

// UUID de tipo (EFFECT_TYPE_NULL = passthrough/pre-processing)
static const effect_uuid_t EFFECT_TYPE_OMEGA = {
    { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }
};

static const effect_descriptor_t OMEGA_DESCRIPTOR = {
    /* .type            = */ EFFECT_TYPE_OMEGA,
    /* .uuid            = */ OMEGA_EFFECT_UUID,
    /* .flags           = */ EFFECT_FLAG_TYPE_PRE_PROC | EFFECT_FLAG_DEVICE_IND,
    /* .cpuLoad         = */ 10,
    /* .memoryUsage     = */ 512,
    /* .name            = */ "Omega In Engine",
    /* .implementor     = */ "IVANNA FUSION"};

// ═══════════════════════════════════════════════════════════════
// CONTEXTO DEL EFECTO (una instancia por sesión de audio)
// ═══════════════════════════════════════════════════════════════
typedef struct {
    const struct effect_interface_s* iface;
    int32_t   session_id;
    int32_t   io_id;
    int       shm_fd;
    OmegaSharedData* shared;
    bool      is_initialized;
} OmegaEffectContext;

// ═══════════════════════════════════════════════════════════════
// FUNCIONES AUXILIARES: SPSC RING BUFFER
// ═══════════════════════════════════════════════════════════════

// Escritura en ring buffer (llamada por el plugin, productor)
// Retorna: número de frames escritos
static size_t ring_write(float* buffer,
                         atomic_size_t* write_pos_ptr,
                         atomic_size_t* read_pos_ptr,
                         const float* src,
                         size_t frames,
                         size_t channels)
{
    size_t total_slots = OMEGA_BUFFER_FRAMES * OMEGA_MAX_CHANNELS;
    size_t samples = frames * channels;
    size_t wpos = atomic_load_explicit(write_pos_ptr, memory_order_relaxed);
    size_t rpos = atomic_load_explicit(read_pos_ptr, memory_order_acquire);

    // Espacio disponible (dejar 1 slot vacío para distinguir lleno de vacío)
    size_t used = (wpos - rpos) % total_slots;
    size_t available = total_slots - used - 1;
    size_t to_write = (samples < available) ? samples : available;

    for (size_t i = 0; i < to_write; ++i) {
        buffer[(wpos + i) % total_slots] = src[i];
    }

    atomic_store_explicit(write_pos_ptr,
                          (wpos + to_write) % total_slots,
                          memory_order_release);
    return to_write / channels;
}

// Lectura de ring buffer (llamada por el plugin, consumidor del output)
// Retorna: número de frames leídos
static size_t ring_read(float* buffer,                        atomic_size_t* write_pos_ptr,
                        atomic_size_t* read_pos_ptr,
                        float* dst,
                        size_t frames,
                        size_t channels)
{
    size_t total_slots = OMEGA_BUFFER_FRAMES * OMEGA_MAX_CHANNELS;
    size_t samples = frames * channels;
    size_t wpos = atomic_load_explicit(write_pos_ptr, memory_order_acquire);
    size_t rpos = atomic_load_explicit(read_pos_ptr, memory_order_relaxed);

    size_t available = (wpos - rpos + total_slots) % total_slots;
    size_t to_read = (samples < available) ? samples : available;

    for (size_t i = 0; i < to_read; ++i) {
        dst[i] = buffer[(rpos + i) % total_slots];
    }

    atomic_store_explicit(read_pos_ptr,
                          (rpos + to_read) % total_slots,
                          memory_order_release);
    return to_read / channels;
}

// ═══════════════════════════════════════════════════════════════
// INTERFAZ AUDIO EFFECT
// ═══════════════════════════════════════════════════════════════

// process(): SOLO mueve datos. CERO inferencia. CERO malloc.
static int32_t omega_process(struct effect_interface_s* self,
                             audio_buffer_t* in_buffer,
                             audio_buffer_t* out_buffer)
{
    if (self == NULL || in_buffer == NULL || out_buffer == NULL) {
        return -EINVAL;
    }

    OmegaEffectContext* ctx = (OmegaEffectContext*)self;

    if (!ctx->is_initialized || ctx->shared == NULL) {
        // Bypass: copiar input a output directamente
        size_t bytes = in_buffer->frameCount * sizeof(float) * OMEGA_MAX_CHANNELS;
        memcpy(out_buffer->f32, in_buffer->f32, bytes);
        return 0;
    }

    OmegaSharedData* shm = ctx->shared;

    // Verificar si el daemon está listo
    bool ready = atomic_load_explicit(&shm->daemon_ready, memory_order_acquire);    bool bypass = atomic_load_explicit(&shm->bypass_mode, memory_order_acquire);

    if (!ready || bypass) {
        // Daemon no listo o bypass activado: audio limpio
        size_t bytes = in_buffer->frameCount * sizeof(float) * OMEGA_MAX_CHANNELS;
        memcpy(out_buffer->f32, in_buffer->f32, bytes);
        return 0;
    }

    // 1. Escribir audio de entrada al ring buffer (plugin → daemon)
    size_t written = ring_write(
        shm->input_buffer,
        &shm->in_write_pos,
        &shm->in_read_pos,
        in_buffer->f32,
        in_buffer->frameCount,
        OMEGA_MAX_CHANNELS
    );

    // 2. Leer audio procesado del ring buffer (daemon → plugin)
    size_t read_frames = ring_read(
        shm->output_buffer,
        &shm->out_write_pos,
        &shm->out_read_pos,
        out_buffer->f32,
        out_buffer->frameCount,
        OMEGA_MAX_CHANNELS
    );

    // 3. Si no hay suficiente audio procesado, llenar el resto con bypass
    if (read_frames < out_buffer->frameCount) {
        size_t remaining = out_buffer->frameCount - read_frames;
        size_t offset_samples = read_frames * OMEGA_MAX_CHANNELS;
        size_t bytes = remaining * sizeof(float) * OMEGA_MAX_CHANNELS;
        memcpy(out_buffer->f32 + offset_samples,
               in_buffer->f32 + offset_samples,
               bytes);

        // Incrementar contador de bloques caídos
        atomic_fetch_add_explicit(&shm->dropped_blocks, 1, memory_order_relaxed);
    }

    return 0;
}

// command(): Manejo de comandos del framework de audio
static int32_t omega_command(struct effect_interface_s* self,
                             uint32_t cmd_code,
                             uint32_t cmd_size,
                             void* cmd_data,                             uint32_t* reply_size,
                             void* reply_data)
{
    if (self == NULL) {
        return -EINVAL;
    }

    switch (cmd_code) {
        case EFFECT_CMD_INIT:
            if (reply_data != NULL && reply_size != NULL && *reply_size >= sizeof(int32_t)) {
                *(int32_t*)reply_data = 0;
                *reply_size = sizeof(int32_t);
            }
            return 0;

        case EFFECT_CMD_SET_CONFIG:
            if (reply_data != NULL && reply_size != NULL && *reply_size >= sizeof(int32_t)) {
                *(int32_t*)reply_data = 0;
                *reply_size = sizeof(int32_t);
            }
            return 0;

        case EFFECT_CMD_RESET:
            return 0;

        case EFFECT_CMD_ENABLE:
            if (reply_data != NULL && reply_size != NULL && *reply_size >= sizeof(int32_t)) {
                *(int32_t*)reply_data = 0;
                *reply_size = sizeof(int32_t);
            }
            return 0;

        case EFFECT_CMD_DISABLE:
            if (reply_data != NULL && reply_size != NULL && *reply_size >= sizeof(int32_t)) {
                *(int32_t*)reply_data = 0;
                *reply_size = sizeof(int32_t);
            }
            return 0;

        case EFFECT_CMD_GET_PARAM:
            if (reply_data != NULL && reply_size != NULL) {
                *reply_size = 0;
            }
            return 0;

        case EFFECT_CMD_SET_PARAM:
            return 0;

        default:
            return -ENOSYS;    }
}

// get_descriptor(): Devuelve información del efecto
static int32_t omega_get_descriptor(struct effect_interface_s* self,
                                    effect_descriptor_t* descriptor)
{
    if (self == NULL || descriptor == NULL) {
        return -EINVAL;
    }
    memcpy(descriptor, &OMEGA_DESCRIPTOR, sizeof(effect_descriptor_t));
    return 0;
}

// Tabla de funciones del efecto
static const struct effect_interface_s OMEGA_EFFECT_INTERFACE = {
    omega_process,
    omega_command,
    omega_get_descriptor
};

// ═══════════════════════════════════════════════════════════════
// FUNCIONES EXPORTADAS DE LA LIBRERÍA
// ═══════════════════════════════════════════════════════════════

extern "C" {

int32_t effect_lib_create(const effect_uuid_t* uuid,
                          int32_t session_id,
                          int32_t io_id,
                          effect_handle_t* handle)
{
    if (uuid == NULL || handle == NULL) {
        return -EINVAL;
    }

    // Verificar UUID
    if (memcmp(uuid, &OMEGA_EFFECT_UUID, sizeof(effect_uuid_t)) != 0) {
        return -ENOENT;
    }

    // Asignar contexto (malloc aquí está OK, solo se llama una vez por sesión)
    OmegaEffectContext* ctx = (OmegaEffectContext*)calloc(1, sizeof(OmegaEffectContext));
    if (ctx == NULL) {
        LOGE("Failed to allocate effect context");
        return -ENOMEM;
    }

    ctx->iface = &OMEGA_EFFECT_INTERFACE;
    ctx->session_id = session_id;    ctx->io_id = io_id;
    ctx->is_initialized = false;
    ctx->shm_fd = -1;
    ctx->shared = NULL;

    // Abrir shared memory
    ctx->shm_fd = open(OMEGA_SHM_PATH, O_RDWR);
    if (ctx->shm_fd >= 0) {
        void* ptr = mmap(NULL, sizeof(OmegaSharedData),
                         PROT_READ | PROT_WRITE, MAP_SHARED,
                         ctx->shm_fd, 0);
        if (ptr != MAP_FAILED) {
            ctx->shared = (OmegaSharedData*)ptr;
            ctx->is_initialized = true;
            LOGI("Effect created: session=%d, io=%d, shm=OK", session_id, io_id);
        } else {
            LOGW("mmap failed: %s, running in bypass", strerror(errno));
            close(ctx->shm_fd);
            ctx->shm_fd = -1;
        }
    } else {
        LOGW("Shared memory not available, running in bypass");
    }

    *handle = (effect_handle_t)ctx;
    return 0;
}

int32_t effect_lib_release(effect_handle_t handle)
{
    if (handle == NULL) {
        return -EINVAL;
    }

    OmegaEffectContext* ctx = (OmegaEffectContext*)handle;

    if (ctx->shared != NULL) {
        munmap(ctx->shared, sizeof(OmegaSharedData));
    }
    if (ctx->shm_fd >= 0) {
        close(ctx->shm_fd);
    }

    free(ctx);
    LOGI("Effect released");
    return 0;
}

int32_t effect_lib_get_descriptor(const effect_uuid_t* uuid,
                                  effect_descriptor_t* descriptor){
    if (uuid == NULL || descriptor == NULL) {
        return -EINVAL;
    }

    if (memcmp(uuid, &OMEGA_EFFECT_UUID, sizeof(effect_uuid_t)) != 0) {
        return -ENOENT;
    }

    memcpy(descriptor, &OMEGA_DESCRIPTOR, sizeof(effect_descriptor_t));
    return 0;
}

} // extern "C"
