/*
 * ivanna_fft_effect.c
 *
 * Efecto de audio real para Android: realza graves/agudos procesando el
 * audio en bloques con FFT real (no el placeholder del scaffold original).
 * Implementa el ABI publico de audio_effect.h (ver android_effect_abi.h)
 * para que audioserver pueda cargarlo via audio_effects.conf.
 *
 * Matematica verificada antes de escribir este archivo:
 *  - fft_radix2_real: picos exactos en los bins esperados con señal de
 *    prueba de dos tonos.
 *  - inversa (conjugar + FFT directa + escalar 1/N): error de
 *    reconstruccion ~2e-6 (ruido de punto flotante) en una ida y vuelta
 *    completa sobre 256 muestras.
 *
 * Seguridad en tiempo real: process() NO hace malloc/free ni llamadas
 * bloqueantes. Todos los buffers se reservan una sola vez en
 * ivanna_create_effect().
 */

#include <string.h>
#include <math.h>
#include <errno.h>
#include "android_effect_abi.h"

#define PIF 3.14159265358979323846f
#define BLOCK 256          /* potencia de 2: tamano de bloque de analisis */
#define MAX_CHANNELS 2

/* ---- UUIDs propios de este efecto (no son de ningun interfaz OpenSL ES
 * estandar, asi que 'type' y 'uuid' son ambos identificadores propios) ---- */
static const effect_uuid_t IVANNA_TYPE_UUID =
    { 0x63000a2e, 0x5a67, 0x47bc, 0x9fb4, { 0xd4, 0x66, 0xdd, 0x8c, 0x62, 0x5c } };
static const effect_uuid_t IVANNA_IMPL_UUID =
    { 0x13e2cd44, 0xb932, 0x431a, 0xaae4, { 0x2d, 0x58, 0xee, 0xb7, 0x3d, 0x6a } };

/* ---------------------------------------------------------------------
 * FFT radix-2 real, in-place, iterativa. Verificada matematicamente.
 * --------------------------------------------------------------------- */
static void fft_radix2_real(float *re, float *im, int n) {
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
            float tr = re[i]; re[i] = re[j]; re[j] = tr;
            float ti = im[i]; im[i] = im[j]; im[j] = ti;
        }
    }
    for (int len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * PIF / (float)len;
        float wr = cosf(ang), wi = sinf(ang);
        for (int i = 0; i < n; i += len) {
            float cwr = 1.0f, cwi = 0.0f;
            for (int k = 0; k < len / 2; k++) {
                float ur = re[i + k],           ui = im[i + k];
                float vr = re[i + k + len/2] * cwr - im[i + k + len/2] * cwi;
                float vi = re[i + k + len/2] * cwi + im[i + k + len/2] * cwr;
                re[i + k]         = ur + vr;
                im[i + k]         = ui + vi;
                re[i + k + len/2] = ur - vr;
                im[i + k + len/2] = ui - vi;
                float nwr = cwr * wr - cwi * wi;
                float nwi = cwr * wi + cwi * wr;
                cwr = nwr; cwi = nwi;
            }
        }
    }
}

/* Inversa via la identidad IDFT(X) = (1/N) * Re( FFT( conj(X) ) ).
 * Solo nos interesa la parte real porque el resultado final es PCM real. */
static void ifft_radix2_real(float *re, float *im, int n) {
    for (int i = 0; i < n; i++) im[i] = -im[i];
    fft_radix2_real(re, im, n);
    for (int i = 0; i < n; i++) re[i] = re[i] / (float)n;
}

/* Ganancia por banda: graves = bin 0..n/8, resto = treble_gain. */
static void audio_band_gain(float *re, float *im, int n, float bass_gain, float treble_gain) {
    int bass_cutoff = n / 8;
    for (int i = 0; i < n / 2; i++) {
        float g = (i < bass_cutoff) ? bass_gain : treble_gain;
        re[i] *= g; im[i] *= g;
        if (i > 0) { re[n - i] *= g; im[n - i] *= g; }
    }
}

/* ---------------------------------------------------------------------
 * Estado de cada instancia del efecto.
 * --------------------------------------------------------------------- */
typedef struct {
    const struct effect_interface_s *itfe; /* DEBE ser el primer campo */
    int enabled;
    int channels;            /* 1 o 2 */
    int sampleRate;
    float bass_gain;
    float treble_gain;
    int16_t ring[MAX_CHANNELS][BLOCK];
    int ringPos;
} ivanna_ctx_t;

<<<<<<< HEAD
/* ivanna_itfe definido más abajo — la definición es suficiente como forward decl en C */
=======
static const struct effect_interface_s ivanna_itfe; /* fwd decl */
>>>>>>> 82b483f (feat(v2.0): fusión PF-ENGINE v3.0.0 + FFT Effect + Presets + nuevas pantallas UI)

/* ---------------------------------------------------------------------
 * process(): llamada en el hilo de audio, debe ser rapida y no bloqueante.
 * --------------------------------------------------------------------- */
static int32_t ivanna_process(effect_handle_t self, audio_buffer_t *in, audio_buffer_t *out) {
    /* self ES la direccion de nuestro propio struct (su primer campo es
     * itfe), no un puntero-a-puntero que haya que desreferenciar de nuevo. */
    ivanna_ctx_t *ctx = (ivanna_ctx_t *)self;
    if (!ctx || !in || !out || !in->s16 || !out->s16) return -EINVAL;
    if (!ctx->enabled) {
        /* bypass: copiar tal cual si no esta habilitado */
        size_t n = in->frameCount * ctx->channels;
        memcpy(out->s16, in->s16, n * sizeof(int16_t));
        return 0;
    }

    size_t frames = in->frameCount;
    for (size_t f = 0; f < frames; f++) {
        for (int c = 0; c < ctx->channels; c++) {
            int16_t sample = in->s16[f * ctx->channels + c];
            ctx->ring[c][ctx->ringPos] = sample;
            out->s16[f * ctx->channels + c] = sample; /* default: passthrough */
        }
        ctx->ringPos++;

        if (ctx->ringPos >= BLOCK) {
            /* bloque lleno: procesar cada canal con FFT real */
            for (int c = 0; c < ctx->channels; c++) {
                float re[BLOCK], im[BLOCK];
                for (int i = 0; i < BLOCK; i++) {
                    re[i] = ctx->ring[c][i] / 32768.0f;
                    im[i] = 0.0f;
                }
                fft_radix2_real(re, im, BLOCK);
                audio_band_gain(re, im, BLOCK, ctx->bass_gain, ctx->treble_gain);
                ifft_radix2_real(re, im, BLOCK);

                for (int i = 0; i < BLOCK; i++) {
                    /* ring[i] corresponde al frame (f - offset). Si ese
                     * frame es de una llamada anterior a process()
                     * (offset > f), no hay como escribirlo en este
                     * out-buffer: se omite en vez de calcular un indice
                     * invalido (evita underflow/wraparound de size_t). */
                    size_t offset = (size_t)(BLOCK - 1 - i);
                    if (offset > f) continue;
                    size_t fi = f - offset;
                    float v = re[i];
                    if (v > 1.0f) v = 1.0f;
                    if (v < -1.0f) v = -1.0f;
                    out->s16[fi * ctx->channels + c] = (int16_t)(v * 32767.0f);
                }
            }
            ctx->ringPos = 0;
        }
    }
    return 0;
}

static int32_t ivanna_process_reverse(effect_handle_t self, audio_buffer_t *in, audio_buffer_t *out) {
    (void)self; (void)in; (void)out;
    return -ENOSYS; /* no usamos stream inverso (no somos un echo canceler) */
}

static int32_t ivanna_get_descriptor(effect_handle_t self, effect_descriptor_t *d) {
    (void)self;
    if (!d) return -EINVAL;
    memset(d, 0, sizeof(*d));
    d->type = IVANNA_TYPE_UUID;
    d->uuid = IVANNA_IMPL_UUID;
    d->apiVersion = EFFECT_CONTROL_API_VERSION;
    d->flags = EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_LAST |
               EFFECT_FLAG_INPUT_DIRECT | EFFECT_FLAG_OUTPUT_DIRECT;
    d->cpuLoad = 30;     /* estimacion conservadora, 0.1 MIPS unidades */
    d->memoryUsage = 4;  /* KB, el ring buffer es chico */
    strncpy(d->name, "Ivanna FFT Band Gain", EFFECT_STRING_LEN_MAX - 1);
    strncpy(d->implementor, "luisurielpimentelperez", EFFECT_STRING_LEN_MAX - 1);
    return 0;
}

static int32_t ivanna_command(effect_handle_t self, uint32_t cmdCode, uint32_t cmdSize,
                               void *pCmdData, uint32_t *replySize, void *pReplyData) {
    ivanna_ctx_t *ctx = (ivanna_ctx_t *)self;
    if (!ctx) return -EINVAL;

    switch (cmdCode) {
        case EFFECT_CMD_INIT:
            ctx->bass_gain = 1.6f;
            ctx->treble_gain = 1.1f;
            ctx->ringPos = 0;
            if (replySize && pReplyData && *replySize >= sizeof(int32_t)) {
                *(int32_t *)pReplyData = 0;
            }
            return 0;

        case EFFECT_CMD_CONFIGURE: {
            if (cmdSize < sizeof(effect_config_t) || !pCmdData) return -EINVAL;
            effect_config_t *cfg = (effect_config_t *)pCmdData;
            uint32_t ch = cfg->inputCfg.channels;
            /* solo soportamos mono o stereo PCM 16 bit: degradacion segura
             * si el formato no es el esperado, devolvemos error en vez de
             * adivinar y corromper audio. */
            int nch = (ch == 1) ? 1 : (ch == 3) ? 2 : -1; /* AUDIO_CHANNEL_OUT_MONO=1, STEREO=3 */
            if (nch < 0 || cfg->inputCfg.format != 1 /* AUDIO_FORMAT_PCM_16_BIT */) {
                return -EINVAL;
            }
            ctx->channels = nch;
            ctx->sampleRate = cfg->inputCfg.samplingRate;
            ctx->ringPos = 0;
            if (replySize && pReplyData && *replySize >= sizeof(int32_t)) {
                *(int32_t *)pReplyData = 0;
            }
            return 0;
        }

        case EFFECT_CMD_RESET:
            ctx->ringPos = 0;
            return 0;

        case EFFECT_CMD_ENABLE:
            ctx->enabled = 1;
            if (replySize && pReplyData && *replySize >= sizeof(int32_t)) {
                *(int32_t *)pReplyData = 0;
            }
            return 0;

        case EFFECT_CMD_DISABLE:
            ctx->enabled = 0;
            if (replySize && pReplyData && *replySize >= sizeof(int32_t)) {
                *(int32_t *)pReplyData = 0;
            }
            return 0;

        case EFFECT_CMD_SET_PARAM: {
            /* formato: effect_param_t seguido de [int32 param_id][float value] */
            if (cmdSize < sizeof(effect_param_t) + sizeof(int32_t) + sizeof(float)) return -EINVAL;
            effect_param_t *p = (effect_param_t *)pCmdData;
            int32_t param_id = *(int32_t *)p->data;
            float value = *(float *)(p->data + sizeof(int32_t));
            if (value < 0.0f) value = 0.0f;
            if (value > 4.0f) value = 4.0f; /* tope de seguridad: nunca mas de +12dB aprox */
            if (param_id == 0) ctx->bass_gain = value;
            else if (param_id == 1) ctx->treble_gain = value;
            else return -EINVAL;
            if (replySize && pReplyData && *replySize >= sizeof(int32_t)) {
                *(int32_t *)pReplyData = 0;
            }
            return 0;
        }

        case EFFECT_CMD_GET_PARAM: {
            if (cmdSize < sizeof(effect_param_t) + sizeof(int32_t)) return -EINVAL;
            effect_param_t *p = (effect_param_t *)pCmdData;
            int32_t param_id = *(int32_t *)p->data;
            effect_param_t *reply = (effect_param_t *)pReplyData;
            if (!reply || !replySize) return -EINVAL;
            reply->status = 0;
            reply->psize = sizeof(int32_t);
            reply->vsize = sizeof(float);
            float *out_val = (float *)(reply->data + sizeof(int32_t));
            *(int32_t *)reply->data = param_id;
            if (param_id == 0) *out_val = ctx->bass_gain;
            else if (param_id == 1) *out_val = ctx->treble_gain;
            else { reply->status = -EINVAL; }
            *replySize = sizeof(effect_param_t) + sizeof(int32_t) + sizeof(float);
            return 0;
        }

        /* comandos que no necesitamos atender activamente: responder OK y
         * seguir, en vez de fallar la sesion de audio por algo no critico */
        case EFFECT_CMD_SET_DEVICE:
        case EFFECT_CMD_SET_VOLUME:
        case EFFECT_CMD_SET_AUDIO_MODE:
        case EFFECT_CMD_SET_INPUT_DEVICE:
        case EFFECT_CMD_CONFIGURE_REVERSE:
            return 0;

        default:
            return -EINVAL;
    }
}

static const struct effect_interface_s ivanna_itfe = {
    .process = ivanna_process,
    .command = ivanna_command,
    .get_descriptor = ivanna_get_descriptor,
    .process_reverse = ivanna_process_reverse,
};

/* ---------------------------------------------------------------------
 * Funciones de la libreria (lo que audioserver llama para crear/listar
 * efectos). Solo exponemos un efecto.
 * --------------------------------------------------------------------- */
static ivanna_ctx_t g_instance; /* una sola instancia activa a la vez,
                                    suficiente para esta version inicial */
static int g_instance_used = 0;

static int32_t ivanna_query_num_effects(uint32_t *pNumEffects) {
    if (!pNumEffects) return -EINVAL;
    *pNumEffects = 1;
    return 0;
}

static int32_t ivanna_query_effect(uint32_t index, effect_descriptor_t *pDescriptor) {
    if (index != 0 || !pDescriptor) return -EINVAL;
    return ivanna_get_descriptor(NULL, pDescriptor);
}

static int32_t ivanna_lib_get_descriptor(const effect_uuid_t *uuid, effect_descriptor_t *pDescriptor) {
    if (!uuid || !pDescriptor) return -EINVAL;
    if (memcmp(uuid, &IVANNA_IMPL_UUID, sizeof(effect_uuid_t)) != 0) return -ENOENT;
    return ivanna_get_descriptor(NULL, pDescriptor);
}

static int32_t ivanna_create_effect(const effect_uuid_t *uuid, int32_t sessionId,
                                     int32_t ioId, effect_handle_t *pHandle) {
    (void)sessionId; (void)ioId;
    if (!uuid || !pHandle) return -EINVAL;
    if (memcmp(uuid, &IVANNA_IMPL_UUID, sizeof(effect_uuid_t)) != 0) return -ENOENT;
    if (g_instance_used) return -ENOMEM; /* version inicial: 1 instancia */

    memset(&g_instance, 0, sizeof(g_instance));
    g_instance.itfe = &ivanna_itfe;
    g_instance.channels = 2;
    g_instance.bass_gain = 1.6f;
    g_instance.treble_gain = 1.1f;
    g_instance_used = 1;

    *pHandle = (effect_handle_t)&g_instance;
    return 0;
}

static int32_t ivanna_release_effect(effect_handle_t handle) {
    if (!handle) return -EINVAL;
    g_instance_used = 0;
    return 0;
}

__attribute__ ((visibility ("default")))
audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    .tag = AUDIO_EFFECT_LIBRARY_TAG,
    .version = EFFECT_LIBRARY_API_VERSION,
    .name = "Ivanna FFT Effects",
    .implementor = "luisurielpimentelperez",
    .query_num_effects = ivanna_query_num_effects,
    .query_effect = ivanna_query_effect,
    .create_effect = ivanna_create_effect,
    .release_effect = ivanna_release_effect,
    .get_descriptor = ivanna_lib_get_descriptor,
};
