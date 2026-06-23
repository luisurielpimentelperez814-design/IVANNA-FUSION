/*
 * hardware/audio_effect.h  —  shim NDK para IVANNA-FUSION
 *
 * <hardware/audio_effect.h> es un header de plataforma AOSP que NO existe
 * en el sysroot del NDK (ni en NDK 25 ni en NDK 27).  El compilador incluye
 * app/src/main/cpp como directorio raíz, así que el #include de omega_effect.cpp:
 *
 *   #include <hardware/audio_effect.h>
 *
 * resuelve a este archivo.  Contiene el layout binario exacto verificado
 * contra AOSP hardware/libhardware/include/hardware/audio_effect.h (rama
 * android-13.0.0_r82).  El orden y tamaño de cada campo es el contrato ABI
 * real — un byte de desalineamiento corrompería la pila de audioserver.
 */
#ifndef HARDWARE_AUDIO_EFFECT_H
#define HARDWARE_AUDIO_EFFECT_H

#include <stdint.h>
#include <stddef.h>

/* ── Identificador 128 bits (UUID estándar, mismo layout de bytes) ─────────── */
typedef struct effect_uuid_s {
    uint32_t timeLow;
    uint16_t timeMid;
    uint16_t timeHiAndVersion;
    uint16_t clockSeq;
    uint8_t  node[6];
} effect_uuid_t;

#define EFFECT_STRING_LEN_MAX  64

/* ── Descriptor del efecto ─────────────────────────────────────────────────── */
typedef struct effect_descriptor_s {
    effect_uuid_t type;
    effect_uuid_t uuid;
    uint32_t      apiVersion;
    uint32_t      flags;
    uint16_t      cpuLoad;
    uint16_t      memoryUsage;
    char          name[EFFECT_STRING_LEN_MAX];
    char          implementor[EFFECT_STRING_LEN_MAX];
} effect_descriptor_t;

/* ── Buffer de audio ───────────────────────────────────────────────────────── */
typedef struct audio_buffer_s audio_buffer_t;
struct audio_buffer_s {
    size_t frameCount;
    union {
        void    *raw;
        int32_t *s32;
        int16_t *s16;
        uint8_t *u8;
        float   *f32;
    };
};

/* ── Vtable del efecto ─────────────────────────────────────────────────────── */
typedef struct effect_interface_s **effect_handle_t;

struct effect_interface_s {
    int32_t (*process)(effect_handle_t self,
                       audio_buffer_t *inBuf, audio_buffer_t *outBuf);
    int32_t (*command)(effect_handle_t self,
                       uint32_t cmdCode, uint32_t cmdSize,
                       void *pCmdData, uint32_t *replySize, void *pReplyData);
    int32_t (*get_descriptor)(effect_handle_t self,
                              effect_descriptor_t *pDescriptor);
    int32_t (*process_reverse)(effect_handle_t self,
                               audio_buffer_t *inBuf, audio_buffer_t *outBuf);
};

/* ── Buffer provider y config ──────────────────────────────────────────────── */
typedef struct buffer_provider_s {
    int32_t (*getBuffer)(void *cookie, audio_buffer_t *buffer);
    int32_t (*releaseBuffer)(void *cookie, audio_buffer_t *buffer);
    void *cookie;
} buffer_provider_t;

typedef struct buffer_config_s {
    audio_buffer_t   buffer;
    uint32_t         samplingRate;
    uint32_t         channels;
    buffer_provider_t bufferProvider;
    uint8_t          format;
    uint8_t          accessMode;
    uint16_t         mask;
} buffer_config_t;

typedef struct effect_config_s {
    buffer_config_t inputCfg;
    buffer_config_t outputCfg;
} effect_config_t;

typedef struct effect_param_s {
    int32_t  status;
    uint32_t psize;
    uint32_t vsize;
    char     data[];
} effect_param_t;

/* ── Comandos ──────────────────────────────────────────────────────────────── */
enum {
    EFFECT_CMD_INIT                 = 0,
    EFFECT_CMD_CONFIGURE            = 1,
    EFFECT_CMD_RESET                = 2,
    EFFECT_CMD_ENABLE               = 3,
    EFFECT_CMD_DISABLE              = 4,
    EFFECT_CMD_SET_PARAM            = 5,
    EFFECT_CMD_SET_PARAM_DEFERRED   = 6,
    EFFECT_CMD_SET_PARAM_COMMIT     = 7,
    EFFECT_CMD_GET_PARAM            = 8,
    EFFECT_CMD_SET_DEVICE           = 9,
    EFFECT_CMD_SET_VOLUME           = 10,
    EFFECT_CMD_SET_AUDIO_MODE       = 11,
    EFFECT_CMD_CONFIGURE_REVERSE    = 12,
    EFFECT_CMD_SET_INPUT_DEVICE     = 13,
    EFFECT_CMD_FIRST_PROPRIETARY    = 0x10000
};

/* ── Flags ─────────────────────────────────────────────────────────────────── */
/* Tipo del efecto (bits 0-2) */
#define EFFECT_FLAG_TYPE_MASK      0x00000007u
#define EFFECT_FLAG_TYPE_INSERT    0x00000000u   /* INSERT = 0 */
#define EFFECT_FLAG_TYPE_AUXILIARY 0x00000001u
#define EFFECT_FLAG_TYPE_REPLACE   0x00000002u
#define EFFECT_FLAG_TYPE_PRE_PROC  0x00000003u
#define EFFECT_FLAG_TYPE_POST_PROC 0x00000004u

/* Posición dentro de la cadena INSERT (bits 3-5) */
#define EFFECT_FLAG_INSERT_MASK    0x00000038u
#define EFFECT_FLAG_INSERT_ANY     0x00000000u
#define EFFECT_FLAG_INSERT_FIRST   0x00000008u
#define EFFECT_FLAG_INSERT_LAST    0x00000010u
#define EFFECT_FLAG_INSERT_EXCLUSIVE 0x00000018u

/* I/O flags */
#define EFFECT_FLAG_INPUT_DIRECT   (1u << 12)
#define EFFECT_FLAG_OUTPUT_DIRECT  (1u << 14)

/* ── Versiones de API ───────────────────────────────────────────────────────── */
#define EFFECT_MAKE_API_VERSION(M,m) (((uint32_t)(M)<<16)|((uint32_t)(m)&0xFFFFu))
#define EFFECT_CONTROL_API_VERSION   EFFECT_MAKE_API_VERSION(2,0)
#define EFFECT_LIBRARY_API_VERSION   EFFECT_MAKE_API_VERSION(1,0)

/* ── Librería de efectos ────────────────────────────────────────────────────── */
#define AUDIO_EFFECT_LIBRARY_TAG \
    ((uint32_t)(('A'<<24)|('E'<<16)|('L'<<8)|'T'))

typedef struct audio_effect_library_s {
    uint32_t   tag;
    uint32_t   version;
    const char *name;
    const char *implementor;
    int32_t (*query_num_effects)(uint32_t *pNumEffects);
    int32_t (*query_effect)(uint32_t index, effect_descriptor_t *pDescriptor);
    int32_t (*create_effect)(const effect_uuid_t *uuid,
                              int32_t sessionId, int32_t ioId,
                              effect_handle_t *pHandle);
    int32_t (*release_effect)(effect_handle_t handle);
    int32_t (*get_descriptor)(const effect_uuid_t *uuid,
                               effect_descriptor_t *pDescriptor);
} audio_effect_library_t;

#endif /* HARDWARE_AUDIO_EFFECT_H */
