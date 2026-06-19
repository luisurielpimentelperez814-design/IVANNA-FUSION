#include "../core/pf_engine.h"
#include <cmath>
#include <cstring>
#include <arm_neon.h>

#define TWOPI 6.28318530717958647692f
#define SR    48000.0f

// ── Biquad IIR filter ────────────────────────────────────────────────
typedef struct { float b0,b1,b2,a1,a2,x1,x2,y1,y2; } Biquad;

static inline float biquad_tick(Biquad *f, float x) {
    float y = f->b0*x + f->b1*f->x1 + f->b2*f->x2
                      - f->a1*f->y1 - f->a2*f->y2;
    f->x2=f->x1; f->x1=x;
    f->y2=f->y1; f->y1=y;
    return y;
}

static void biquad_lowshelf(Biquad *f, float freq, float gain_db) {
    float A  = powf(10.0f, gain_db/40.0f);
    float w0 = TWOPI*freq/SR;
    float cw = cosf(w0), sw = sinf(w0);
    float b  = sqrtf(A)*sw;
    float a0 = (A+1)+(A-1)*cw+b;
    f->b0 = A*((A+1)-(A-1)*cw+b)/a0;
    f->b1 = 2*A*((A-1)-(A+1)*cw)/a0;
    f->b2 = A*((A+1)-(A-1)*cw-b)/a0;
    f->a1 = -2*((A-1)+(A+1)*cw)/a0;
    f->a2 = ((A+1)+(A-1)*cw-b)/a0;
}

static void biquad_peak(Biquad *f, float freq, float q, float gain_db) {
    float A  = powf(10.0f, gain_db/40.0f);
    float w0 = TWOPI*freq/SR;
    float al = sinf(w0)/(2.0f*q);
    float cw = cosf(w0);
    float a0 = 1.0f + al/A;
    f->b0 = (1.0f + al*A)/a0;
    f->b1 = (-2.0f*cw)/a0;
    f->b2 = (1.0f - al*A)/a0;
    f->a1 = f->b1;
    f->a2 = (1.0f - al/A)/a0;
}

static void biquad_highshelf(Biquad *f, float freq, float gain_db) {
    float A  = powf(10.0f, gain_db/40.0f);
    float w0 = TWOPI*freq/SR;
    float cw = cosf(w0), sw = sinf(w0);
    float b  = sqrtf(A)*sw;
    float a0 = (A+1)-(A-1)*cw+b;
    f->b0 = A*((A+1)+(A-1)*cw+b)/a0;
    f->b1 = -2*A*((A-1)+(A+1)*cw)/a0;
    f->b2 = A*((A+1)+(A-1)*cw-b)/a0;
    f->a1 = 2*((A-1)-(A+1)*cw)/a0;
    f->a2 = ((A+1)-(A-1)*cw-b)/a0;
}

// ── Per-channel DSP state ────────────────────────────────────────────
typedef struct {
    Biquad eq_low, eq_mid, eq_high, eq_presence;
    float  dc_block_x1, dc_block_y1;
} ChannelDSP;

static ChannelDSP g_ch[BUS_COUNT];

static inline float dc_block(ChannelDSP *c, float x) {
    float y = x - c->dc_block_x1 + 0.995f * c->dc_block_y1;
    c->dc_block_x1 = x;
    c->dc_block_y1 = y;
    return y;
}

// ── Waveshaping / amp modeling ───────────────────────────────────────
static inline float tanh_approx(float x) {
    /* Padé approximation — avoids libm overhead */
    float x2 = x*x;
    return x*(27.0f+x2)/(27.0f+9.0f*x2);
}

static inline float arctan_shape(float x, float drive) {
    return (2.0f/3.14159265f) * atanf(x * drive * 1.5f);
}

static inline float cubic_clip(float x) {
    if (x >  1.0f) return  2.0f/3.0f;
    if (x < -1.0f) return -2.0f/3.0f;
    return x - (x*x*x)/3.0f;
}

// ── Harmonic saturation ──────────────────────────────────────────────
static inline float harmonics(float x, float beta) {
    return x + beta * 0.15f * (x*x - x*x*x*0.33f);
}

// ── Amp model selector ───────────────────────────────────────────────
float pf_amp_process(float x, const PFParams *p) {
    float d   = p->drive;
    float wet = p->wet;
    float dry = x;

    switch (p->amp) {
    case AMP_MARSHALL:
        /* High-gain: hard asymmetric clip + tanh */
        x = harmonics(x, p->beta + 0.3f);
        x = tanh_approx(x * (1.5f + d));
        x = cubic_clip(x * (0.8f + p->delta * 0.4f));
        break;
    case AMP_FENDER:
        /* Clean warm: soft arctan, minimal harmonics */
        x = arctan_shape(x, 0.6f + d * 0.4f);
        x = harmonics(x, p->beta * 0.3f);
        break;
    case AMP_VOX:
        /* Mid sparkle: tanh + presence boost harmonics */
        x = tanh_approx(x * (1.0f + d));
        x = harmonics(x, p->beta + p->presence * 0.2f);
        break;
    case AMP_ROCK70S:
        /* Full stack: cubic → tanh cascade */
        x = cubic_clip(x * (1.2f + d * 0.6f));
        x = tanh_approx(x * (1.0f + p->delta));
        x = harmonics(x, p->beta + 0.2f);
        break;
    default:
        return x;
    }

    /* Sag simulation: soft level compression on drive peaks */
    float sag_gain = 1.0f / (1.0f + p->sag * fabsf(x) * 0.3f);
    x *= sag_gain;

    return dry + wet * (x - dry);
}

// ── EQ rebuild from params ───────────────────────────────────────────
void pf_rebuild_eq(PFBusID bus, const PFParams *p) {
    ChannelDSP *c = &g_ch[bus];
    biquad_lowshelf (&c->eq_low,      120.0f,    p->low_gain);
    biquad_peak     (&c->eq_mid,      p->mid_freq, 1.2f, p->mid_gain);
    biquad_highshelf(&c->eq_high,     6000.0f,   p->high_gain);
    biquad_peak     (&c->eq_presence, 3500.0f,   2.5f, p->presence);
}

// ── Full DSP chain per sample ────────────────────────────────────────
float pf_chain_sample(PFBusID bus, float x, const PFParams *p) {
    ChannelDSP *c = &g_ch[bus];
    x = dc_block(c, x);
    x = biquad_tick(&c->eq_low,      x);
    x = biquad_tick(&c->eq_mid,      x);
    x = pf_amp_process(x, p);
    x = biquad_tick(&c->eq_high,     x);
    x = biquad_tick(&c->eq_presence, x);
    x *= p->alpha;
    return x;
}

// ── SIMD frame processing (NEON) ────────────────────────────────────
void pf_process_frame_neon(float *buf, uint32_t n, float gain) {
    float32x4_t g4 = vdupq_n_f32(gain);
    uint32_t i = 0;
    for (; i+4 <= n; i += 4) {
        float32x4_t v = vld1q_f32(buf+i);
        v = vmulq_f32(v, g4);
        vst1q_f32(buf+i, v);
    }
    for (; i < n; i++) buf[i] *= gain;
}
