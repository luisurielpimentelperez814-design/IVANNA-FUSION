#pragma once
#include <stdint.h>
#include <stdbool.h>
#ifdef __cplusplus
extern "C" {
#endif

#define PF_VERSION     "3.0.0"
#define PF_FRAME_SIZE  256
#define PF_SAMPLE_RATE 48000
#define PF_SOCKET_PATH "/data/pf/pf.sock"
#define PF_PRESET_DIR  "/data/pf/presets/"
#define PF_LOG_PATH    "/data/pf/pf.log"

typedef enum {
    AMP_MARSHALL=0, AMP_FENDER=1, AMP_VOX=2, AMP_ROCK70S=3, AMP_BYPASS=4
} PFAmpModel;

typedef enum {
    BUS_GUITAR=0, BUS_BASS=1, BUS_DRUMS=2, BUS_VOCAL=3, BUS_MASTER=4, BUS_COUNT=5
} PFBusID;

typedef struct {
    float alpha;     /* spectral tilt        */
    float beta;      /* harmonic density     */
    float gamma;     /* transient shaping    */
    float delta;     /* distortion depth     */
    float sigma;     /* spatial width        */
    float drive;     /* amp drive 0.0-4.0    */
    float wet;       /* wet/dry  0.0-1.0     */
    float low_gain;  /* EQ low  dB           */
    float mid_gain;  /* EQ mid  dB           */
    float high_gain; /* EQ high dB           */
    float mid_freq;  /* EQ mid center Hz     */
    float presence;  /* upper-mid boost      */
    float sag;       /* amp sag simulation   */
    float bias;      /* tube bias point      */
    PFAmpModel amp;
} PFParams;

typedef struct {
    float    volume, pan;
    PFParams dsp;
    bool     mute, solo;
} PFBusState;

typedef struct {
    PFParams   master;
    PFBusState bus[BUS_COUNT];
    uint32_t   bar, sample_rate, frame_size;
    bool       learning_active;
    char       preset_name[64];
} PFEngineState;

int            pf_init(uint32_t sr, uint32_t fs);
void           pf_shutdown(void);
void           pf_process(float **in, float **out, uint32_t frames, uint32_t ch);
void           pf_process_bus(PFBusID bus, float *in, float *out, uint32_t frames);
void           pf_set_params(const PFParams *p);
void           pf_get_params(PFParams *p);
void           pf_set_bus_params(PFBusID bus, const PFParams *p);
void           pf_set_amp(PFAmpModel model);
int            pf_parse_command(const char *cmd);
int            pf_load_preset(const char *name);
int            pf_save_preset(const char *name);
PFEngineState *pf_get_state(void);
void           pf_advance_bar(void);

#ifdef __cplusplus
}
#endif
