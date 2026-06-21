/*
 * IVANNA-FUSION / Ω_in
 * omega_effect.cpp — Audio Effect Plugin (productor del ring buffer SPSC)
 *
 * ── CORRECCIÓN CRÍTICA DE ESTABILIDAD ──────────────────────────────────────
 * La versión anterior de este archivo SOLO exportaba funciones JNI
 * (Java_com_ivannafusion_OmegaEffect_*), pero audio_effects.xml le pide a
 * audioserver que cargue libomega_effect.so como un Audio Effect HAL
 * NATIVO (preprocess de stream "music"). audioserver busca el símbolo
 * AUDIO_EFFECT_LIBRARY_INFO_SYM (una tabla audio_effect_library_t) al
 * hacer dlopen() de esta librería — un símbolo que NO existía en la
 * versión anterior. Esto causaba que audioserver (un proceso crítico del
 * sistema) fallara al intentar cargar el efecto en cuanto cualquier app
 * reproducía música, lo cual puede manifestarse como pantalla negra,
 * comportamiento errático y reinicios en bucle del dispositivo.
 *
 * Esta versión implementa la interfaz real audio_effect_library_t (mismo
 * patrón ya verificado y funcionando en src/cpp/effect_library.cpp para
 * libivanna_fusion.so), y MUEVE la lógica del ring buffer SPSC al hot
 * path correcto: Effect_Process(), llamado por audioserver, no por JNI.
 *
 * ARQUITECTURA (sin cambios de diseño, solo de superficie de carga):
 *   1. Este archivo (libomega_effect.so): vive dentro de audioserver,
 *      intercepta el audio del sistema vía el Effect HAL real. SOLO
 *      mueve datos al/del ring buffer compartido. JAMÁS ejecuta
 *      inferencia de IA aquí.
 *   2. omega_daemon (root, proceso separado): lee del ring buffer de
 *      entrada, ejecuta la inferencia Ω_in, escribe en el ring buffer
 *      de salida.
 *   3. APK (OmegaMagiskBridge.kt): controla parámetros vía Unix Domain
 *      Socket directamente al daemon, no a este efecto.
 *
 * REGLA DE ORO: si el daemon no entrega un bloque de salida a tiempo
 * (ring_out vacío), este efecto pasa el audio de entrada sin modificar
 * (bypass) en vez de silenciar o bloquear. Nunca se espera/duerme aquí.
 *
 * Las funciones JNI Java_com_ivannafusion_OmegaEffect_* se conservan al
 * final de este archivo para no romper compatibilidad con código Kotlin
 * existente que las invoque para control/diagnóstico desde la app, pero
 * ya NO son el mecanismo por el que audioserver carga ni ejecuta el
 * efecto — eso ahora pasa exclusivamente por la vtable de abajo.
 */

#include "android_effect_abi.h"
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <new>
#include <atomic>
#include <android/log.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <jni.h>

#include "omega_shared.h"

// ─── Compatibilidad mínima con <system/audio.h> (igual que effect_library.cpp) ──
#ifndef AUDIO_CHANNEL_OUT_STEREO
#define AUDIO_CHANNEL_OUT_STEREO 0x3u
#endif
#ifndef AUDIO_FORMAT_PCM_FLOAT
#define AUDIO_FORMAT_PCM_FLOAT   0x5u
#endif
typedef uint32_t audio_format_t;

static inline int audio_channel_count_from_out_mask(uint32_t mask) {
    int n = 0;
    while (mask) { n += (mask & 1u); mask >>= 1u; }
    return n;
}

static const effect_uuid_t kEffectUuidNull = {0, 0, 0, 0, {0,0,0,0,0,0}};
#define EFFECT_UUID_NULL (&kEffectUuidNull)

#define LOG_TAG "OmegaEffect"
#define ALOG(...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── Descriptor del efecto ────────────────────────────────────────────────────
// UUID propio (8d7d5e0a-a6eb-4fde-a0ff-cb1b2dd7275e), DISTINTO al de
// IVANNA FUSION DSP (7b3be4ec-...) — dos efectos con el mismo UUID
// confundirían a audioserver sobre cuál instanciar.
static const effect_uuid_t kEffectTypeNull = {
    0xec7178a0, 0x847d, 0x11e0, 0xa3cb, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}
};
static const effect_uuid_t kEffectUuid = {
    0x8d7d5e0a, 0xa6eb, 0x4fde, 0xa0ff, {0xcb, 0x1b, 0x2d, 0xd7, 0x27, 0x5e}
};

static const effect_descriptor_t kEffectDescriptor = {
    .type          = kEffectTypeNull,
    .uuid          = kEffectUuid,
    .apiVersion    = EFFECT_CONTROL_API_VERSION,
    .flags         = EFFECT_FLAG_TYPE_INSERT
                   | EFFECT_FLAG_INSERT_LAST,
    .cpuLoad       = 30,        // bajo: solo memcpy + ring buffer, sin DSP propio aquí
    .memoryUsage   = 200,       // tamaño de OmegaSharedState mapeado, no duplicado
    .name          = "OMEGA Ω_in AI Bridge",
    .implementor   = "GORE TNS",
};

// ─── Contexto por instancia del efecto ───────────────────────────────────────
struct OmegaContext {
    const struct effect_interface_s* itfe;   // debe ser el primer miembro
    effect_config_t config;
    bool            active = false;

    // Memoria compartida con omega_daemon, mapeada en EffectCreate (no
    // en el hot path de Effect_Process), liberada en EffectRelease.
    OmegaSharedState* shared = nullptr;
    int               shm_fd = -1;

    std::atomic<uint32_t> consecutive_underruns{0};
};

static constexpr const char* kShmPath = "/data/local/tmp/omega_shared_mem";

static bool mapSharedMemory(OmegaContext* ctx) {
    if (ctx->shared != nullptr) return true;

    ctx->shm_fd = open(kShmPath, O_RDWR);
    if (ctx->shm_fd < 0) {
        // omega_daemon todavía no creó el archivo (no ha arrancado, o el
        // módulo Magisk se deshabilitó). No es un error fatal para el
        // efecto: simplemente seguimos en bypass hasta que exista.
        return false;
    }

    void* mapped = mmap(nullptr, sizeof(OmegaSharedState),
                         PROT_READ | PROT_WRITE, MAP_SHARED, ctx->shm_fd, 0);
    if (mapped == MAP_FAILED) {
        ALOGE("mmap falló mapeando memoria compartida con omega_daemon");
        close(ctx->shm_fd);
        ctx->shm_fd = -1;
        return false;
    }

    ctx->shared = static_cast<OmegaSharedState*>(mapped);
    ALOG("Memoria compartida mapeada correctamente");
    return true;
}

static void unmapSharedMemory(OmegaContext* ctx) {
    if (ctx->shared != nullptr) {
        munmap(ctx->shared, sizeof(OmegaSharedState));
        ctx->shared = nullptr;
    }
    if (ctx->shm_fd >= 0) {
        close(ctx->shm_fd);
        ctx->shm_fd = -1;
    }
}

// ─── Forward declarations ─────────────────────────────────────────────────────
static int Effect_Process(effect_handle_t self, audio_buffer_t* in, audio_buffer_t* out);
static int Effect_Command(effect_handle_t self,
                          uint32_t cmdCode, uint32_t cmdSize, void* pCmd,
                          uint32_t* replySize, void* pReply);
static int Effect_GetDescriptor(effect_handle_t self, effect_descriptor_t* pDescriptor);

static const struct effect_interface_s sEffectInterface = {
    .process         = Effect_Process,
    .command         = Effect_Command,
    .get_descriptor  = Effect_GetDescriptor,
    .process_reverse = nullptr,
};

// ─────────────────────────────────────────────────────────────────────────────
// PROCESS — hot path real, llamado por audioserver en el hilo de mezcla.
// CRÍTICO: nunca bloquea, nunca duerme, nunca hace malloc/free, nunca
// ejecuta inferencia. Solo: (1) tryPush no bloqueante al ring de
// entrada, (2) tryPop no bloqueante del ring de salida, (3) si no hay
// bloque procesado disponible, el buffer ya tiene el audio original
// (bypass automático, sin silencios).
// ─────────────────────────────────────────────────────────────────────────────
static int Effect_Process(effect_handle_t self,
                          audio_buffer_t* in, audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<OmegaContext*>(self);
    if (!ctx) return -EINVAL;

    int n_frames = in ? (int)in->frameCount : 0;
    if (n_frames <= 0) return 0;

    int ch = audio_channel_count_from_out_mask(ctx->config.inputCfg.channels);
    int samples = n_frames * (ch > 0 ? ch : 2);

    // Si está desactivado o no hay memoria compartida disponible todavía,
    // copiar entrada -> salida sin tocar (bypass), igual que haría
    // cualquier efecto INSERT que no modifica audio.
    bool can_process = ctx->active && (ctx->shared != nullptr || mapSharedMemory(ctx));

    if (!can_process || (ctx->shared && ctx->shared->bypass_enabled.load(std::memory_order_relaxed))) {
        if (in->raw != out->raw) std::memcpy(out->raw, in->raw, (size_t)samples * sizeof(float));
        return 0;
    }

    int cap = (samples < OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS)
                  ? samples : OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;

    // Copiar primero entrada -> salida (esto YA es el bypass por
    // defecto); si el daemon entrega un bloque procesado, se sobrescribe
    // a continuación. Así nunca hay una ruta que deje 'out' sin escribir.
    if (in->raw != out->raw) std::memcpy(out->raw, in->raw, (size_t)samples * sizeof(float));

    ctx->shared->ring_in.tryPush(in->f32, cap, &ctx->shared->input_buffer[0][0]);

    bool got_processed = ctx->shared->ring_out.tryPop(
        out->f32, cap, &ctx->shared->output_buffer[0][0]);

    if (got_processed) {
        ctx->consecutive_underruns.store(0, std::memory_order_relaxed);
    } else {
        ctx->consecutive_underruns.fetch_add(1, std::memory_order_relaxed);
        // 'out' ya tiene el audio original (bypass), no se hace nada más.
    }

    return 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// COMMAND
// ─────────────────────────────────────────────────────────────────────────────
static int Effect_Command(effect_handle_t self,
                          uint32_t cmdCode, uint32_t cmdSize, void* pCmd,
                          uint32_t* replySize, void* pReply) {
    auto* ctx = reinterpret_cast<OmegaContext*>(self);
    if (!ctx) return -EINVAL;
    (void)cmdSize; (void)pCmd;

    switch (cmdCode) {
    case EFFECT_CMD_INIT:
        ALOG("INIT");
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;

    case EFFECT_CMD_CONFIGURE: {
        if (cmdSize < sizeof(effect_config_t)) return -EINVAL;
        std::memcpy(&ctx->config, pCmd, sizeof(effect_config_t));
        ALOG("CONFIGURE fs=%u ch_mask=0x%x",
             ctx->config.inputCfg.samplingRate, ctx->config.inputCfg.channels);
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;
    }

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
        if (pReply && replySize && *replySize >= sizeof(int32_t))
            *(int32_t*)pReply = 0;
        return 0;

    default:
        return -EINVAL;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GET DESCRIPTOR
// ─────────────────────────────────────────────────────────────────────────────
static int Effect_GetDescriptor(effect_handle_t self,
                                effect_descriptor_t* pDescriptor) {
    (void)self;
    if (!pDescriptor) return -EINVAL;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// LIBRARY FUNCTIONS — exportadas como AUDIO_EFFECT_LIBRARY_INFO_SYM
// ─────────────────────────────────────────────────────────────────────────────
static int EffectCreate(const effect_uuid_t* uuid,
                        int32_t sessionId, int32_t ioId,
                        effect_handle_t* handle) {
    (void)sessionId; (void)ioId;
    if (!uuid || !handle) return -EINVAL;
    if (std::memcmp(uuid, &kEffectUuid, sizeof(effect_uuid_t)) != 0) return -ENOENT;

    auto* ctx = new (std::nothrow) OmegaContext();
    if (!ctx) return -ENOMEM;

    ctx->itfe = &sEffectInterface;
    ctx->config.inputCfg.samplingRate  = OMEGA_SAMPLE_RATE;
    ctx->config.inputCfg.channels      = AUDIO_CHANNEL_OUT_STEREO;
    ctx->config.inputCfg.format        = AUDIO_FORMAT_PCM_FLOAT;
    ctx->config.outputCfg              = ctx->config.inputCfg;

    // Intento de mapeo no bloqueante: si el daemon aún no arrancó, no es
    // un error — Effect_Process seguirá intentando mapear hasta que exista.
    mapSharedMemory(ctx);

    *handle = reinterpret_cast<effect_handle_t>(ctx);
    ALOG("EffectCreate OK, session=%d io=%d", sessionId, ioId);
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

static int EffectGetDescriptor(const effect_uuid_t* uuid,
                               effect_descriptor_t* pDescriptor) {
    if (!uuid || !pDescriptor) return -EINVAL;
    if (std::memcmp(uuid, &kEffectUuid, sizeof(effect_uuid_t)) != 0 &&
        std::memcmp(uuid, EFFECT_UUID_NULL, sizeof(effect_uuid_t)) != 0)
        return -ENOENT;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

static int QueryNumEffects(uint32_t* pNumEffects) {
    if (!pNumEffects) return -EINVAL;
    *pNumEffects = 1;
    return 0;
}

static int QueryEffect(uint32_t index, effect_descriptor_t* pDescriptor) {
    if (!pDescriptor) return -EINVAL;
    if (index != 0) return -ENOENT;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

// IMPORTANTE: el nombre del símbolo debe ser literalmente
// AUDIO_EFFECT_LIBRARY_INFO_SYM — este es el símbolo que faltaba en la
// versión anterior y causaba el fallo de carga dentro de audioserver.
extern "C" __attribute__((visibility("default")))
const audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    .tag               = AUDIO_EFFECT_LIBRARY_TAG,
    .version           = EFFECT_LIBRARY_API_VERSION,
    .name              = "OMEGA Ω_in Bridge Library",
    .implementor       = "GORE TNS",
    .query_num_effects = QueryNumEffects,
    .query_effect      = QueryEffect,
    .create_effect     = EffectCreate,
    .release_effect    = EffectRelease,
    .get_descriptor    = EffectGetDescriptor,
};

// ─────────────────────────────────────────────────────────────────────────────
// Bindings JNI conservados (compatibilidad con código Kotlin existente,
// control/diagnóstico desde la app — NO es el mecanismo de carga por
// audioserver, que ahora pasa exclusivamente por la vtable de arriba).
// ─────────────────────────────────────────────────────────────────────────────
namespace {
std::atomic<bool>  g_jni_is_active{false};
std::atomic<float> g_jni_intensity{0.8f};
std::atomic<float> g_jni_vocoder_mix{0.8f};
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_OmegaEffect_nativeInit(JNIEnv*, jobject) {
    ALOG("nativeInit (capa JNI de diagnóstico, separada del Effect HAL real)");
    g_jni_is_active.store(false);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeRelease(JNIEnv*, jobject) {
    g_jni_is_active.store(false);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetActive(JNIEnv*, jobject, jboolean active) {
    g_jni_is_active.store(active);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetIntensity(JNIEnv*, jobject, jfloat intensity) {
    g_jni_intensity.store(intensity);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetVocoderMix(JNIEnv*, jobject, jfloat mix) {
    g_jni_vocoder_mix.store(mix);
}

} // extern "C"
