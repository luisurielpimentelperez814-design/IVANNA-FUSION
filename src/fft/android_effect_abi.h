/*
 * android_effect_abi.h
 *
 * Definiciones de tipos para implementar una libreria de efecto de audio
 * cargable por audioserver. El layout de cada struct (orden y tamano de
 * campos) esta verificado contra el codigo fuente real de AOSP
 * (hardware/libhardware/include/hardware/audio_effect.h), no escrito de
 * memoria, porque un desalineamiento de un solo campo aqui corrompe la
 * pila de audioserver en cuanto se llama una funcion de la interfaz.
 *
 * Los comentarios estan reescritos en mis propias palabras; los nombres y
 * tipos de campo se mantienen exactos porque son el contrato binario, no
 * texto descriptivo.
 */
#ifndef ANDROID_EFFECT_ABI_H
#define ANDROID_EFFECT_ABI_H

#include <stdint.h>
#include <stddef.h>

/* Identificador de 128 bits, mismo layout que un UUID estandar. */
typedef struct effect_uuid_s {
    uint32_t timeLow;
    uint16_t timeMid;
    uint16_t timeHiAndVersion;
    uint16_t clockSeq;
    uint8_t  node[6];
} effect_uuid_t;

#define EFFECT_STRING_LEN_MAX 64

/* Descriptor que audioserver pide via get_descriptor() para saber que es
 * este efecto y como tratarlo (flags de insercion, costo de CPU, etc). */
typedef struct effect_descriptor_s {
    effect_uuid_t type;
    effect_uuid_t uuid;
    uint32_t apiVersion;
    uint32_t flags;
    uint16_t cpuLoad;
    uint16_t memoryUsage;
    char name[EFFECT_STRING_LEN_MAX];
    char implementor[EFFECT_STRING_LEN_MAX];
} effect_descriptor_t;

typedef struct effect_interface_s **effect_handle_t;
typedef struct audio_buffer_s audio_buffer_t;

struct audio_buffer_s {
    size_t frameCount;
    union {
        void    *raw;
        int32_t *s32;
        int16_t *s16;
        uint8_t *u8;
        float   *f32;   // alias para PCM float — no cambia tamaño ni offset de la unión
    };
};

/* Tabla de funciones que audioserver invoca sobre cada instancia del
 * efecto. process_reverse puede ser NULL si no se usa (no lo usamos). */
struct effect_interface_s {
    int32_t (*process)(effect_handle_t self, audio_buffer_t *inBuf, audio_buffer_t *outBuf);
    int32_t (*command)(effect_handle_t self, uint32_t cmdCode, uint32_t cmdSize,
                        void *pCmdData, uint32_t *replySize, void *pReplyData);
    int32_t (*get_descriptor)(effect_handle_t self, effect_descriptor_t *pDescriptor);
    int32_t (*process_reverse)(effect_handle_t self, audio_buffer_t *inBuf, audio_buffer_t *outBuf);
};

enum {
    EFFECT_CMD_INIT,
    EFFECT_CMD_CONFIGURE,
    EFFECT_CMD_RESET,
    EFFECT_CMD_ENABLE,
    EFFECT_CMD_DISABLE,
    EFFECT_CMD_SET_PARAM,
    EFFECT_CMD_SET_PARAM_DEFERRED,
    EFFECT_CMD_SET_PARAM_COMMIT,
    EFFECT_CMD_GET_PARAM,
    EFFECT_CMD_SET_DEVICE,
    EFFECT_CMD_SET_VOLUME,
    EFFECT_CMD_SET_AUDIO_MODE,
    EFFECT_CMD_CONFIGURE_REVERSE,
    EFFECT_CMD_SET_INPUT_DEVICE,
    EFFECT_CMD_FIRST_PROPRIETARY = 0x10000
};

typedef struct buffer_provider_s {
    int32_t (*getBuffer)(void *cookie, audio_buffer_t *buffer);
    int32_t (*releaseBuffer)(void *cookie, audio_buffer_t *buffer);
    void *cookie;
} buffer_provider_t;

typedef struct buffer_config_s {
    audio_buffer_t buffer;
    uint32_t samplingRate;
    uint32_t channels;
    buffer_provider_t bufferProvider;
    uint8_t format;
    uint8_t accessMode;
    uint16_t mask;
} buffer_config_t;

typedef struct effect_config_s {
    buffer_config_t inputCfg;
    buffer_config_t outputCfg;
} effect_config_t;

typedef struct effect_param_s {
    int32_t status;
    uint32_t psize;
    uint32_t vsize;
    char data[];
} effect_param_t;

#define EFFECT_FLAG_TYPE_INSERT   0
#define EFFECT_FLAG_INSERT_LAST   (2 << 3)
#define EFFECT_FLAG_INPUT_DIRECT  (1 << 12)
#define EFFECT_FLAG_OUTPUT_DIRECT (1 << 14)

#define EFFECT_MAKE_API_VERSION(M, m) (((M) << 16) | ((m) & 0xFFFF))
#define EFFECT_CONTROL_API_VERSION    EFFECT_MAKE_API_VERSION(2, 0)
#define EFFECT_LIBRARY_API_VERSION    EFFECT_MAKE_API_VERSION(2, 0)
#define AUDIO_EFFECT_LIBRARY_TAG      ((('A')<<24)|(('E')<<16)|(('L')<<8)|('T'))

/* Punto de entrada que audioserver busca por nombre de simbolo
 * (AUDIO_EFFECT_LIBRARY_INFO_SYM) al hacer dlopen() de la libreria. */
typedef struct audio_effect_library_s {
    uint32_t tag;
    uint32_t version;
    const char *name;
    const char *implementor;
    int32_t (*query_num_effects)(uint32_t *pNumEffects);
    int32_t (*query_effect)(uint32_t index, effect_descriptor_t *pDescriptor);
    int32_t (*create_effect)(const effect_uuid_t *uuid, int32_t sessionId,
                              int32_t ioId, effect_handle_t *pHandle);
    int32_t (*release_effect)(effect_handle_t handle);
    int32_t (*get_descriptor)(const effect_uuid_t *uuid, effect_descriptor_t *pDescriptor);
} audio_effect_library_t;

#define AUDIO_EFFECT_LIBRARY_INFO_SYM AELI

#endif /* ANDROID_EFFECT_ABI_H */
