// effect_library.cpp — Plugin Android Audio Effect
// Implementa audio_effect_library_t para ser cargado por AudioFlinger
//
// Registro: /system/etc/audio_effects_ivanna.xml (parcheado por customize.sh)
// UUID:      7b3be4ec-c23c-4e6e-8c6d-49e5f4d54ea3
//
// Compatibilidad: Android 8.0+ (API 26+), ARMv8a
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include "android_effect_abi.h"
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <cerrno>
#include <new>
#include <android/log.h>

#include "ivanna_fusion.h"

// ─── Compatibilidad mínima con <system/audio.h> ──────────────────────────────
// android_effect_abi.h no trae las máscaras de canal ni audio_format_t de
// <system/audio.h> (header AOSP interno, no disponible en el NDK público).
// Se definen aquí solo los valores mínimos que usa este archivo.
#ifndef AUDIO_CHANNEL_OUT_STEREO
#define AUDIO_CHANNEL_OUT_STEREO 0x3u   // FL | FR, layout AOSP estándar
#endif
#ifndef AUDIO_FORMAT_PCM_FLOAT
#define AUDIO_FORMAT_PCM_FLOAT   0x5u   // mismo valor que audio_format_t de AOSP
#endif
#ifndef AUDIO_FORMAT_PCM_16_BIT
#define AUDIO_FORMAT_PCM_16_BIT  0x1u
#endif
typedef uint32_t audio_format_t;

static inline int audio_channel_count_from_out_mask(uint32_t mask) {
    int n = 0;
    while (mask) { n += (mask & 1u); mask >>= 1u; }
    return n;
}

// EFFECT_UUID_NULL: UUID nulo estándar (todo ceros) usado por audioserver
// para pedir el descriptor de "cualquier efecto" via get_descriptor() con
// uuid genérico. No forma parte del layout binario de ningún struct —
// es solo un valor de comparación — así que no hay riesgo de
// desalineamiento al definirlo aquí.
static const effect_uuid_t kEffectUuidNull = {0, 0, 0, 0, {0,0,0,0,0,0}};
#define EFFECT_UUID_NULL (&kEffectUuidNull)

#define LOG_TAG "IvannaFusionDSP"
#define ALOG(...)   __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define ALOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── Descriptor del efecto ────────────────────────────────────────────────────
// Tipo: EFFECT_TYPE_NULL (sin tipo estándar — efecto propietario completo)
// UUID único para IVANNA FUSION DSP

static const effect_uuid_t kEffectTypeNull = {
    0xec7178a0, 0x847d, 0x11e0, 0xa3cb, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}
};
static const effect_uuid_t kEffectUuid = {
    0x7b3be4ec, 0xc23c, 0x4e6e, 0x8c6d, {0x49, 0xe5, 0xf4, 0xd5, 0x4e, 0xa3}
};

static const effect_descriptor_t kEffectDescriptor = {
    .type          = kEffectTypeNull,
    .uuid          = kEffectUuid,
    .apiVersion    = EFFECT_CONTROL_API_VERSION,
    .flags         = EFFECT_FLAG_TYPE_INSERT
                   | EFFECT_FLAG_INSERT_LAST,
    // Nota: se quitó EFFECT_FLAG_VOLUME_CTRL — no está definido en el ABI
    // local (android_effect_abi.h) ni se usa en ivanna_fft_effect.c, y no
    // hay forma de verificar su valor numérico real sin el header AOSP
    // completo. Un flag incorrecto aquí puede hacer que audioserver
    // interprete mal las capacidades del efecto. El control de volumen vía
    // EFFECT_CMD_SET_VOLUME sigue funcionando sin este flag — solo deja de
    // anunciarse como capacidad explícita ante audioserver.
    .cpuLoad       = 120,       // estimado: 1.2% CPU en Snapdragon 4 Gen 2
    .memoryUsage   = 400,       // ~400 KB
    .name          = "IVANNA FUSION DSP",
    .implementor   = "GORE TNS",
};

// ─── Contexto por instancia del efecto ───────────────────────────────────────
struct IvannaContext {
    const struct effect_interface_s* itfe;   // debe ser el primer miembro
    effect_config_t       config;
    ivanna::IvannaFusionEngine engine;
    bool                  active = false;

    // Buffers de trabajo por instancia (antes eran 'static' a nivel de
    // función en Effect_Process, compartidos entre TODAS las instancias
    // del efecto -> si audioserver crea más de un stream simultáneo con
    // este efecto activo, los datos de audio de un stream sobrescriben
    // los del otro mientras ambos procesan. Ahora cada IvannaContext
    // tiene su propio espacio de trabajo).
    static constexpr int kMaxBlock = 4096;
    float scratch_l_[kMaxBlock];
    float scratch_r_[kMaxBlock];
    float scratch_m_[kMaxBlock];   // usado para el path mono
};

// ─── Forward declarations ─────────────────────────────────────────────────────
static int Effect_Process(effect_handle_t self,
                          audio_buffer_t* in, audio_buffer_t* out);
static int Effect_Command(effect_handle_t self,
                          uint32_t cmdCode, uint32_t cmdSize, void* pCmd,
                          uint32_t* replySize, void* pReply);
static int Effect_GetDescriptor(effect_handle_t self,
                                effect_descriptor_t* pDescriptor);

// ─── vtable del efecto ────────────────────────────────────────────────────────
static const struct effect_interface_s sEffectInterface = {
    .process        = Effect_Process,
    .command        = Effect_Command,
    .get_descriptor = Effect_GetDescriptor,
    .process_reverse = nullptr,
};

// ─────────────────────────────────────────────────────────────────────────────
// PROCESS — hot path, llamado por AudioFlinger en el hilo de mezcla
// ─────────────────────────────────────────────────────────────────────────────
static int Effect_Process(effect_handle_t self,
                          audio_buffer_t* in, audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<IvannaContext*>(self);
    if (!ctx || !ctx->active) return -EINVAL;
    if (!in || !out || !in->f32 || !out->f32) return -EINVAL;

    int n_frames = (int)in->frameCount;
    if (n_frames <= 0) return 0;

    // Diagnóstico: si AudioFlinger entrega más frames que kMaxBlock,
    // el procesamiento se divide correctamente en chunks (ver bucles
    // 'while (n_frames > 0)' más abajo), pero antes esto ocurría sin
    // dejar ningún rastro. Lo registramos una sola vez por llamada
    // para poder diagnosticar tamaños de buffer inusuales en campo
    // sin saturar el log en cada callback de audio.
    if (n_frames > IvannaContext::kMaxBlock) {
        ALOG("Effect_Process: n_frames=%d excede kMaxBlock=%d, procesando en chunks",
             n_frames, IvannaContext::kMaxBlock);
    }

    const audio_format_t fmt = ctx->config.inputCfg.format;

    if (fmt == AUDIO_FORMAT_PCM_FLOAT) {
        // ── Float 32: formato ideal, sin conversión ───────────────────────
        int ch = audio_channel_count_from_out_mask(ctx->config.inputCfg.channels);
        if (ch == 2) {
            // Stereo intercalado → de-interleave on stack
            // AudioFlinger usa frames de 240-480 típicamente
            // Para frames > kMaxBlock procesamos en chunks
            float* tmpL = ctx->scratch_l_;
            float* tmpR = ctx->scratch_r_;

            while (n_frames > 0) {
                int chunk = (n_frames < IvannaContext::kMaxBlock) ? n_frames : IvannaContext::kMaxBlock;
                // De-interleave
                for (int i = 0; i < chunk; ++i) {
                    tmpL[i] = in->f32[i*2    ];
                    tmpR[i] = in->f32[i*2 + 1];
                }
                // Procesar
                ctx->engine.process(tmpL, tmpR, tmpL, tmpR, chunk);
                // Re-interleave
                for (int i = 0; i < chunk; ++i) {
                    out->f32[i*2    ] = tmpL[i];
                    out->f32[i*2 + 1] = tmpR[i];
                }
                in->f32    += chunk * 2;
                out->f32   += chunk * 2;
                n_frames   -= chunk;
            }
        } else if (ch == 1) {
            // Mono: L=R
            float* tmpM = ctx->scratch_m_;
            while (n_frames > 0) {
                int chunk = (n_frames < IvannaContext::kMaxBlock) ? n_frames : IvannaContext::kMaxBlock;
                std::memcpy(tmpM, in->f32, chunk * sizeof(float));
                ctx->engine.process(tmpM, tmpM, tmpM, tmpM, chunk);
                std::memcpy(out->f32, tmpM, chunk * sizeof(float));
                in->f32  += chunk;
                out->f32 += chunk;
                n_frames -= chunk;
            }
        }
    } else if (fmt == AUDIO_FORMAT_PCM_16_BIT) {
        // ── S16 → float → procesar → S16 ────────────────────────────────
        float* tmpL = ctx->scratch_l_;
        float* tmpR = ctx->scratch_r_;
        const int16_t* src = in->s16;
        int16_t*       dst = out->s16;
        const float scale   = 1.f / 32768.f;
        const float inv_sc  = 32767.f;

        while (n_frames > 0) {
            int chunk = (n_frames < IvannaContext::kMaxBlock) ? n_frames : IvannaContext::kMaxBlock;
            for (int i = 0; i < chunk; ++i) {
                tmpL[i] = src[i*2    ] * scale;
                tmpR[i] = src[i*2 + 1] * scale;
            }
            ctx->engine.process(tmpL, tmpR, tmpL, tmpR, chunk);
            for (int i = 0; i < chunk; ++i) {
                float l = tmpL[i] * inv_sc;
                float r = tmpR[i] * inv_sc;
                // Clip a [-32768, 32767]
                dst[i*2    ] = (int16_t)(l > 32767.f ? 32767 : l < -32768.f ? -32768 : l);
                dst[i*2 + 1] = (int16_t)(r > 32767.f ? 32767 : r < -32768.f ? -32768 : r);
            }
            src      += chunk * 2;
            dst      += chunk * 2;
            n_frames -= chunk;
        }
    }

    return 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// COMMAND — recibe comandos de control de AudioFlinger / app
// ─────────────────────────────────────────────────────────────────────────────
static int Effect_Command(effect_handle_t self,
                          uint32_t cmdCode, uint32_t cmdSize, void* pCmd,
                          uint32_t* replySize, void* pReply) {
    auto* ctx = reinterpret_cast<IvannaContext*>(self);
    if (!ctx) return -EINVAL;

    switch (cmdCode) {

    case EFFECT_CMD_INIT:
        ALOG("INIT");
        ctx->engine.reset();
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;

    case EFFECT_CMD_CONFIGURE: {
        // Nota: se usa EFFECT_CMD_CONFIGURE (confirmado en android_effect_abi.h)
        // en vez de un SET_CONFIG/GET_CONFIG separados que no estaban
        // verificados contra el ABI local. CONFIGURE recibe el mismo
        // effect_config_t y cubre el mismo propósito de fijar samplerate/canales.
        if (cmdSize < sizeof(effect_config_t)) return -EINVAL;
        std::memcpy(&ctx->config, pCmd, sizeof(effect_config_t));
        float fs = (float)ctx->config.inputCfg.samplingRate;
        if (fs < 8000.f || fs > 384000.f) fs = 48000.f;
        int ch = audio_channel_count_from_out_mask(ctx->config.inputCfg.channels);
        ctx->engine.init(fs, ch);
        ALOG("CONFIGURE fs=%.0f ch=%d fmt=0x%x", fs, ch, ctx->config.inputCfg.format);
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = 0;
        return 0;
    }

    case EFFECT_CMD_RESET:
        ctx->engine.reset();
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

    case EFFECT_CMD_GET_PARAM: {
        // pCmd apunta a effect_param_t con psize=4 (int param_id), vsize=4 (float)
        if (!pCmd || cmdSize < sizeof(effect_param_t)) return -EINVAL;
        auto* param = reinterpret_cast<effect_param_t*>(pCmd);
        if (param->psize != sizeof(int32_t)) return -EINVAL;
        int32_t param_id = *(int32_t*)param->data;
        float value = 0.f;
        ctx->engine.getParam(param_id, value);
        if (pReply && replySize && *replySize >= sizeof(effect_param_t) + sizeof(float)) {
            auto* rep = reinterpret_cast<effect_param_t*>(pReply);
            rep->psize = sizeof(int32_t);
            rep->vsize = sizeof(float);
            rep->status = 0;
            *(int32_t*)rep->data = param_id;
            *(float*)(rep->data + sizeof(int32_t)) = value;
        }
        return 0;
    }

    case EFFECT_CMD_SET_PARAM: {
        if (!pCmd || cmdSize < sizeof(effect_param_t) + sizeof(int32_t) + sizeof(float))
            return -EINVAL;
        auto* param = reinterpret_cast<effect_param_t*>(pCmd);
        if (param->psize < sizeof(int32_t)) return -EINVAL;
        int32_t param_id = *(int32_t*)param->data;
        float   value    = *(float*)(param->data + param->psize);
        bool ok = ctx->engine.setParam(param_id, value);
        if (replySize && *replySize >= sizeof(int)) *(int*)pReply = ok ? 0 : -EINVAL;
        return 0;
    }

    case EFFECT_CMD_SET_VOLUME:
        // Aceptamos control de volumen (EFFECT_FLAG_VOLUME_CTRL)
        if (pReply && replySize && *replySize >= sizeof(int32_t))
            *(int32_t*)pReply = 0;
        return 0;

    // Nota: GET_LATENCY y GET_DESCRIPTOR no son códigos de EFFECT_CMD_* del
    // ABI estándar de AOSP servidos vía command(); GET_DESCRIPTOR se expone
    // a través del puntero get_descriptor de la vtable (ver Effect_GetDescriptor
    // más abajo), que es lo que audioserver invoca realmente. No se inventa
    // un valor de comando aquí porque un código incorrecto puede colisionar
    // con EFFECT_CMD_SET_INPUT_DEVICE u otros ya reservados.

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
                        int32_t             sessionId,
                        int32_t             ioId,
                        effect_handle_t*    handle) {
    (void)sessionId; (void)ioId;
    if (!uuid || !handle) return -EINVAL;
    if (std::memcmp(uuid, &kEffectUuid, sizeof(effect_uuid_t)) != 0) return -ENOENT;

    auto* ctx = new (std::nothrow) IvannaContext();
    if (!ctx) return -ENOMEM;

    ctx->itfe = &sEffectInterface;
    // Config por defecto: stereo float 48kHz
    ctx->config.inputCfg.samplingRate  = 48000;
    ctx->config.inputCfg.channels      = AUDIO_CHANNEL_OUT_STEREO;
    ctx->config.inputCfg.format        = AUDIO_FORMAT_PCM_FLOAT;
    ctx->config.outputCfg              = ctx->config.inputCfg;
    ctx->engine.init(48000.f, 2);

    *handle = reinterpret_cast<effect_handle_t>(ctx);
    ALOG("EffectCreate OK, session=%d io=%d", sessionId, ioId);
    return 0;
}

static int EffectRelease(effect_handle_t handle) {
    if (!handle) return -EINVAL;
    delete reinterpret_cast<IvannaContext*>(handle);
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

// ─── Funciones requeridas por audio_effect_library_t ──────────────────────────
// query_num_effects: indica cuántos efectos expone esta librería (solo 1)
static int QueryNumEffects(uint32_t* pNumEffects) {
    if (!pNumEffects) return -EINVAL;
    *pNumEffects = 1;
    return 0;
}

// query_effect: devuelve el descriptor del efecto en el índice dado
static int QueryEffect(uint32_t index, effect_descriptor_t* pDescriptor) {
    if (!pDescriptor) return -EINVAL;
    if (index != 0) return -ENOENT;
    std::memcpy(pDescriptor, &kEffectDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

// ─── Símbolo exportado — AudioFlinger busca exactamente este nombre ───────────
// IMPORTANTE: El nombre del símbolo debe ser literalmente AUDIO_EFFECT_LIBRARY_INFO_SYM
// para que audioserver lo encuentre al hacer dlopen() y las herramientas de
// verificación (nm, readelf) lo detecten correctamente.
extern "C" __attribute__((visibility("default")))
const audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    .tag               = AUDIO_EFFECT_LIBRARY_TAG,
    .version           = EFFECT_LIBRARY_API_VERSION,
    .name              = "IVANNA FUSION DSP Library",
    .implementor       = "GORE TNS",
    .query_num_effects = QueryNumEffects,
    .query_effect      = QueryEffect,
    .create_effect     = EffectCreate,
    .release_effect    = EffectRelease,
    .get_descriptor    = EffectGetDescriptor,
};
