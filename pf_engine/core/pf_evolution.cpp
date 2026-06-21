#include "pf_evolution.h"
#include <cmath>
#include <cstring>

#define SMOOTH 0.18f   /* max change per frame < 0.2 */

static float lerp(float a, float b, float t) { return a + (b-a)*t; }

static PFParams params_lerp(const PFParams *a, const PFParams *b, float t) {
    PFParams r;
    r.alpha     = lerp(a->alpha,    b->alpha,    t);
    r.beta      = lerp(a->beta,     b->beta,     t);
    r.gamma     = lerp(a->gamma,    b->gamma,    t);
    r.delta     = lerp(a->delta,    b->delta,    t);
    r.sigma     = lerp(a->sigma,    b->sigma,    t);
    r.drive     = lerp(a->drive,    b->drive,    t);
    r.wet       = lerp(a->wet,      b->wet,      t);
    r.low_gain  = lerp(a->low_gain, b->low_gain, t);
    r.mid_gain  = lerp(a->mid_gain, b->mid_gain, t);
    r.high_gain = lerp(a->high_gain,b->high_gain,t);
    r.mid_freq  = lerp(a->mid_freq, b->mid_freq, t);
    r.presence  = lerp(a->presence, b->presence, t);
    r.sag       = lerp(a->sag,      b->sag,      t);
    r.bias      = lerp(a->bias,     b->bias,     t);
    r.amp       = (t < 0.5f) ? a->amp : b->amp;
    return r;
}

void pf_evo_interp(const PFParams *a, const PFParams *b, float t, PFParams *out) {
    *out = params_lerp(a, b, t);
}

void pf_evo_init(PFEvolutionCurve *c, const PFParams *base) {
    c->baseline = *base;

    /* BUILD: +energy, slight drive increase */
    c->build          = *base;
    c->build.drive   += 0.40f;
    c->build.delta   += 0.15f;
    c->build.beta    += 0.10f;
    c->build.wet     += 0.10f;
    c->build.low_gain+= 1.5f;

    /* PEAK: full saturation */
    c->peak           = c->build;
    c->peak.drive    += 0.60f;
    c->peak.delta    += 0.20f;
    c->peak.wet      += 0.15f;
    c->peak.presence += 2.0f;
    c->peak.high_gain+= 2.0f;

    /* DECAY: pull back toward baseline */
    c->decay          = *base;
    c->decay.wet      = base->wet + 0.08f;   /* stabilised wet */
    c->decay.drive    = base->drive + 0.10f;
    c->decay.presence = base->presence + 0.5f;
}

void pf_evo_tick(PFEvolutionCurve *c, uint32_t bar, PFParams *out) {
    float t;

    if (bar < EVO_BAR_BUILD) {
        /* 0→16  baseline */
        *out = c->baseline;
        return;
    }
    if (bar < EVO_BAR_PEAK) {
        /* 16→32  build */
        t = (float)(bar - EVO_BAR_BUILD) / (float)(EVO_BAR_PEAK - EVO_BAR_BUILD);
        *out = params_lerp(&c->baseline, &c->build, t);
        return;
    }
    if (bar < EVO_BAR_DECAY) {
        /* 32→48  peak */
        t = (float)(bar - EVO_BAR_PEAK) / (float)(EVO_BAR_DECAY - EVO_BAR_PEAK);
        *out = params_lerp(&c->build, &c->peak, t);
        return;
    }
    /* 48+  decay */
    float decay_bars = 32.0f;
    t = fminf((float)(bar - EVO_BAR_DECAY) / decay_bars, 1.0f);
    *out = params_lerp(&c->peak, &c->decay, t);
}
