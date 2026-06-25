/*
 * IVANNA-FUSION / Ω_in
 * omega_effect.cpp — Audio Effect Plugin (productor del ring buffer SPSC)
 *
 * CAMBIO CRÍTICO: mapSharedMemory() ahora usa SCM_RIGHTS en lugar de
 * open("/data/local/tmp/omega_shared_mem").
 *
 * La versión anterior fallaba porque audioserver (UID=1041, dominio SELinux
 * "audioserver") no tiene permiso para abrir shell_data_file (/data/local/tmp).
 * El open() devolvía EACCES, mmap() sobre fd=-1 producía comportamiento
 * indefinido (SIGSEGV o MAP_FAILED en función del compilador), colapsando
 * audioserver → reinicio en bucle del sistema de audio / pantalla negra.
 *
 * Solución: omega_daemon crea la shm con memfd_create() y la pasa vía
 * SCM_RIGHTS en el primer mensaje del socket abstracto Unix. audioserver
 * puede recibir un fd via socket sin ninguna restricción SELinux adicional.
 * El efecto conecta al socket, lee el fd, mapea y cierra inmediatamente la
 * conexión — el canal de comandos lo gestiona la APK por separado.
 *
 * ARQUITECTURA (sin cambios de diseño):
 *   1. Este archivo (libomega_effect.so): vive en audioserver, intercepta
 *      audio vía Effect HAL real. SOLO mueve datos al/del ring buffer.
 *   2. omega_daemon (root, ejecutable standalone del módulo Magisk): lee
 *      del ring_in, ejecuta inferencia, escribe en ring_out.
 *   3. APK (OmegaMagiskBridge.kt): controla parámetros vía el mismo socket.
 */

#include "audio_effect_compat.h"
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <new>
#include <atomic>
#include <android/log.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <jni.h>
#include "omega_shared.h"

#ifndef AUDIO_CHANNEL_OUT_STEREO
#define AUDIO_CHANNEL_OUT_STEREO 0x3u
#endif
#ifndef AUDIO_FORMAT_PCM_FLOAT
#define AUDIO_FORMAT_PCM_FLOAT   0x5u
#endif

#define LOG_TAG "OmegaEffect"
#define ALOG(...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr const char* kSocketName = "omega_daemon_socket";

// ── Descriptor ───────────────────────────────────────────────────────────────
static const effect_uuid_t kEffectTypeNull = {
    0xec7178a0, 0x847d, 0x11e0, 0xa3cb, {0x00,0x02,0xa5,0xd5,0xc5,0x1b}
};
static const effect_uuid_t kEffectUuid = {
    0x8d7d5e0a, 0xa6eb, 0x4fde, 0xa0ff, {0xcb,0x1b,0x2d,0xd7,0x27,0x5e}
};
static const effect_descriptor_t kEffectDescriptor = {
    .type          = kEffectTypeNull,
    .uuid          = kEffectUuid,
    .apiVersion    = EFFECT_CONTROL_API_VERSION,
    .flags         = EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_LAST,
    .cpuLoad       = 30,
    .memoryUsage   = 200,
    .name          = "OMEGA Omega_in AI Bridge",
    .implementor   = "GORE TNS",
};

// ── Contexto por instancia ────────────────────────────────────────────────────
struct OmegaContext {
    const struct effect_interface_s* itfe;
    effect_config_t config;
    bool            active = false;
    OmegaSharedState* shared = nullptr;
    int               shm_fd = -1;
    std::atomic<uint32_t> consecutive_underruns{0};
};

// ── SCM_RIGHTS: recibir fd del daemon ────────────────────────────────────────
static int receive_shm_fd_from_daemon() {
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) return -1;

    // Timeout de 200 ms para no bloquear el hilo de audioserver si el
    // daemon aún no ha arrancado — se reintentará en el siguiente frame.
    struct timeval tv = { 0, 200000 };
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, kSocketName, sizeof(addr.sun_path) - 2);
    socklen_t addrlen = (socklen_t)(sizeof(addr.sun_family) + 1 + strlen(kSocketName));

    if (connect(sock, reinterpret_cast<sockaddr*>(&addr), addrlen) < 0) {
        close(sock);
        return -1;  // daemon no disponible aún — retry en el siguiente frame
    }

    // recvmsg con ancillary data para recibir el fd vía SCM_RIGHTS
    char  data = 0;
    struct iovec iov = { &data, 1 };
    char cmsgbuf[CMSG_SPACE(sizeof(int))];
    struct msghdr msg = {};
    msg.msg_iov        = &iov;
    msg.msg_iovlen     = 1;
    msg.msg_control    = cmsgbuf;
    msg.msg_controllen = sizeof(cmsgbuf);

    if (recvmsg(sock, &msg, 0) < 0) {
        close(sock);
        return -1;
    }
    close(sock);  // fd de shm ya recibido; no necesitamos el socket

    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    if (!cmsg || cmsg->cmsg_level != SOL_SOCKET || cmsg->cmsg_type != SCM_RIGHTS) {
        ALOGE("SCM_RIGHTS no recibido del daemon");
        return -1;
    }
    int received_fd = -1;
    memcpy(&received_fd, CMSG_DATA(cmsg), sizeof(int));
    return received_fd;
}

// ── Mapeo de memoria compartida ───────────────────────────────────────────────
static bool mapSharedMemory(OmegaContext* ctx) {
    if (ctx->shared != nullptr) return true;

    int shm_fd = receive_shm_fd_from_daemon();
    if (shm_fd < 0) return false;  // daemon no listo — bypass hasta el próximo intento

    void* mapped = mmap(nullptr, sizeof(OmegaSharedState),
                        PROT_READ | PROT_WRITE, MAP_SHARED, shm_fd, 0);
    close(shm_fd);  // mmap retiene la referencia internamente

    if (mapped == MAP_FAILED) {
        ALOGE("mmap falló sobre fd recibido via SCM_RIGHTS (errno=%d)", errno);
        return false;
    }
    ctx->shm_fd = -1;  // ya no guardamos el fd (mmap lo duplicó internamente)
    ctx->shared = static_cast<OmegaSharedState*>(mapped);
    ALOG("Memoria compartida mapeada via SCM_RIGHTS OK (size=%zu)", sizeof(OmegaSharedState));
    return true;
}

static void unmapSharedMemory(OmegaContext* ctx) {
    if (ctx->shared) { munmap(ctx->shared, sizeof(OmegaSharedState)); ctx->shared = nullptr; }
}

// ── Forward decls ─────────────────────────────────────────────────────────────
static int Effect_Process(effect_handle_t, audio_buffer_t*, audio_buffer_t*);
static int Effect_Command(effect_handle_t, uint32_t, uint32_t, void*, uint32_t*, void*);
static int Effect_GetDescriptor(effect_handle_t, effect_descriptor_t*);

static const struct effect_interface_s sEffectInterface = {
    .process         = Effect_Process,
    .command         = Effect_Command,
    .get_descriptor  = Effect_GetDescriptor,
    .process_reverse = nullptr,
};

// ── PROCESS — hot path ────────────────────────────────────────────────────────
static int Effect_Process(effect_handle_t self,
                          audio_buffer_t* in, audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<OmegaContext*>(self);
    if (!ctx || !in) return -EINVAL;
    int n_frames = (int)in->frameCount;
    if (n_frames <= 0) return 0;

    int ch      = audio_channel_count_from_out_mask(ctx->config.inputCfg.channels);
    int samples = n_frames * (ch > 0 ? ch : 2);

    // Bypass automático si el daemon aún no entregó la shm
    bool can_process = ctx->active &&
                       (ctx->shared != nullptr || mapSharedMemory(ctx));

    if (!can_process ||
        (ctx->shared && ctx->shared->bypass_enabled.load(std::memory_order_relaxed))) {
        if (in->raw != out->raw)
            std::memcpy(out->raw, in->raw, (size_t)samples * sizeof(float));
        return 0;
    }

    int cap = (samples < OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS)
                  ? samples : OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;

    if (in->raw != out->raw)
        std::memcpy(out->raw, in->raw, (size_t)samples * sizeof(float));

    ctx->shared->ring_in.tryPush(in->f32, cap, &ctx->shared->input_buffer[0][0]);

    bool got = ctx->shared->ring_out.tryPop(out->f32, cap, &ctx->shared->output_buffer[0][0]);
    if (got) ctx->consecutive_underruns.store(0, std::memory_order_relaxed);
    else     ctx->consecutive_underruns.fetch_add(1, std::memory_order_relaxed);

    return 0;
}

// ── COMMAND ───────────────────────────────────────────────────────────────────
static int Effect_Command(effect_handle_t self,
                          uint32_t cmdCode, uint32_t cmdSize, void* pCmd,
                          uint32_t* replySize, void* pReply) {
    auto* ctx = reinterpret_cast<OmegaContext*>(self);
    if (!ctx) return -EINVAL;
    (void)cmdSize; (void)pCmd;

    switch (cmdCode) {
    case EFFECT_CMD_INIT:
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        ALOG("INIT");
        return 0;
    case EFFECT_CMD_CONFIGURE:
        if (cmdSize < sizeof(effect_config_t)) return -EINVAL;
        std::memcpy(&ctx->config, pCmd, sizeof(effect_config_t));
        ALOG("CONFIGURE fs=%u ch_mask=0x%x",
             ctx->config.inputCfg.samplingRate, ctx->config.inputCfg.channels);
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;
    case EFFECT_CMD_RESET:
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;
    case EFFECT_CMD_ENABLE:
        ctx->active = true;
        ALOG("ENABLE");
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;
    case EFFECT_CMD_DISABLE:
        ctx->active = false;
        ALOG("DISABLE");
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;
    case EFFECT_CMD_SET_VOLUME:
        if (pReply && replySize && *replySize >= sizeof(int32_t)) *(int32_t*)pReply = 0;
        return 0;
    default:
        return -EINVAL;
    }
}

// ── GET_DESCRIPTOR ────────────────────────────────────────────────────────────
static int Effect_GetDescriptor(effect_handle_t self, effect_descriptor_t* pDescriptor) {
    (void)self;
    if (!pDescriptor) return -EINVAL;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

// ── Library functions ─────────────────────────────────────────────────────────
static int EffectCreate(const effect_uuid_t* uuid,
                        int32_t sessionId, int32_t ioId,
                        effect_handle_t* handle) {
    (void)sessionId; (void)ioId;
    if (!uuid || !handle) return -EINVAL;
    if (std::memcmp(uuid, &kEffectUuid, sizeof(effect_uuid_t)) != 0) return -ENOENT;
    auto* ctx = new (std::nothrow) OmegaContext();
    if (!ctx) return -ENOMEM;
    ctx->itfe = &sEffectInterface;
    ctx->config.inputCfg.samplingRate = OMEGA_SAMPLE_RATE;
    ctx->config.inputCfg.channels     = AUDIO_CHANNEL_OUT_STEREO;
    ctx->config.inputCfg.format       = AUDIO_FORMAT_PCM_FLOAT;
    ctx->config.outputCfg             = ctx->config.inputCfg;
    mapSharedMemory(ctx);  // intento no bloqueante — si el daemon no está listo, bypass
    *handle = reinterpret_cast<effect_handle_t>(ctx);
    ALOG("EffectCreate OK session=%d io=%d", sessionId, ioId);
    return 0;
}

static int EffectRelease(effect_handle_t handle) {
    if (!handle) return -EINVAL;
    auto* ctx = reinterpret_cast<OmegaContext*>(handle);
    unmapSharedMemory(ctx);
    delete ctx;
    ALOG("EffectRelease OK");
    return 0;
}

static int EffectGetDescriptor(const effect_uuid_t* uuid, effect_descriptor_t* pDescriptor) {
    if (!uuid || !pDescriptor) return -EINVAL;
    if (std::memcmp(uuid, &kEffectUuid,    sizeof(effect_uuid_t)) != 0 &&
        std::memcmp(uuid, EFFECT_UUID_NULL, sizeof(effect_uuid_t)) != 0) return -ENOENT;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

static int QueryNumEffects(uint32_t* pNumEffects) {
    if (!pNumEffects) return -EINVAL;
    *pNumEffects = 1;
    return 0;
}

static int QueryEffect(uint32_t index, effect_descriptor_t* pDescriptor) {
    if (!pDescriptor || index != 0) return -ENOENT;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

extern "C" __attribute__((visibility("default")))
const audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    .tag               = AUDIO_EFFECT_LIBRARY_TAG,
    .version           = EFFECT_LIBRARY_API_VERSION,
    .name              = "OMEGA Omega_in Bridge Library",
    .implementor       = "GORE TNS",
    .query_num_effects = QueryNumEffects,
    .query_effect      = QueryEffect,
    .create_effect     = EffectCreate,
    .release_effect    = EffectRelease,
    .get_descriptor    = EffectGetDescriptor,
};

// ── JNI de diagnóstico (compatibilidad con Kotlin) ────────────────────────────
namespace {
std::atomic<bool>  g_jni_is_active{false};
std::atomic<float> g_jni_intensity{0.8f};
std::atomic<float> g_jni_vocoder_mix{0.8f};
}

extern "C" {
JNIEXPORT jboolean JNICALL Java_com_ivannafusion_OmegaEffect_nativeInit(JNIEnv*, jobject) {
    g_jni_is_active.store(false);
    ALOG("nativeInit (JNI diagnóstico)");
    return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_com_ivannafusion_OmegaEffect_nativeRelease(JNIEnv*, jobject) {
    g_jni_is_active.store(false);
}
JNIEXPORT void JNICALL Java_com_ivannafusion_OmegaEffect_nativeSetActive(JNIEnv*, jobject, jboolean a) {
    g_jni_is_active.store(a);
}
JNIEXPORT void JNICALL Java_com_ivannafusion_OmegaEffect_nativeSetIntensity(JNIEnv*, jobject, jfloat v) {
    g_jni_intensity.store(v);
}
JNIEXPORT void JNICALL Java_com_ivannafusion_OmegaEffect_nativeSetVocoderMix(JNIEnv*, jobject, jfloat v) {
    g_jni_vocoder_mix.store(v);
}
} // extern "C"
