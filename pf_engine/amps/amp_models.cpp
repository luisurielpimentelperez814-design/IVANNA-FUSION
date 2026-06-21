#include "../core/pf_engine.h"
#include <cmath>

/* ── Default amp configs ─────────────────────────────────────────── */
PFParams amp_preset_marshall(void) {
    PFParams p = {};
    p.amp       = AMP_MARSHALL;
    p.drive     = 3.2f;
    p.wet       = 0.85f;
    p.delta     = 0.75f;
    p.beta      = 0.55f;
    p.sigma     = 0.40f;
    p.alpha     = 1.10f;
    p.gamma     = 0.60f;
    p.low_gain  = 3.0f;
    p.mid_gain  = -1.5f;
    p.high_gain = 4.0f;
    p.mid_freq  = 700.0f;
    p.presence  = 5.0f;
    p.sag       = 0.25f;
    p.bias      = 0.45f;
    return p;
}

PFParams amp_preset_fender(void) {
    PFParams p = {};
    p.amp       = AMP_FENDER;
    p.drive     = 0.8f;
    p.wet       = 0.65f;
    p.delta     = 0.10f;
    p.beta      = 0.15f;
    p.sigma     = 0.70f;
    p.alpha     = 0.95f;
    p.gamma     = 0.40f;
    p.low_gain  = 2.0f;
    p.mid_gain  = 0.5f;
    p.high_gain = 1.5f;
    p.mid_freq  = 500.0f;
    p.presence  = 1.5f;
    p.sag       = 0.05f;
    p.bias      = 0.55f;
    return p;
}

PFParams amp_preset_vox(void) {
    PFParams p = {};
    p.amp       = AMP_VOX;
    p.drive     = 1.8f;
    p.wet       = 0.70f;
    p.delta     = 0.35f;
    p.beta      = 0.40f;
    p.sigma     = 0.55f;
    p.alpha     = 1.00f;
    p.gamma     = 0.50f;
    p.low_gain  = 0.5f;
    p.mid_gain  = 3.5f;
    p.high_gain = 2.0f;
    p.mid_freq  = 1200.0f;
    p.presence  = 3.0f;
    p.sag       = 0.12f;
    p.bias      = 0.50f;
    return p;
}

PFParams amp_preset_rock70s(void) {
    PFParams p = {};
    p.amp       = AMP_ROCK70S;
    p.drive     = 2.8f;
    p.wet       = 0.80f;
    p.delta     = 0.60f;
    p.beta      = 0.50f;
    p.sigma     = 0.50f;
    p.alpha     = 1.05f;
    p.gamma     = 0.65f;
    p.low_gain  = 4.0f;
    p.mid_gain  = 1.0f;
    p.high_gain = 3.0f;
    p.mid_freq  = 900.0f;
    p.presence  = 4.0f;
    p.sag       = 0.20f;
    p.bias      = 0.48f;
    return p;
}

PFParams amp_get_preset(PFAmpModel model) {
    switch (model) {
    case AMP_MARSHALL: return amp_preset_marshall();
    case AMP_FENDER:   return amp_preset_fender();
    case AMP_VOX:      return amp_preset_vox();
    case AMP_ROCK70S:  return amp_preset_rock70s();
    default:           return PFParams{};
    }
}

/* ── Per-amp bus tuning ───────────────────────────────────────────── */
void amp_tune_bus(PFAmpModel model, PFBusID bus, PFParams *p) {
    switch (bus) {
    case BUS_GUITAR:
        *p = amp_get_preset(model);
        break;
    case BUS_BASS:
        *p = amp_get_preset(model);
        p->low_gain  += 4.0f;
        p->high_gain -= 3.0f;
        p->mid_freq   = 300.0f;
        p->drive     *= 0.7f;
        p->presence   = 0.5f;
        break;
    case BUS_DRUMS:
        p->drive    = 1.2f;
        p->delta    = 0.30f;
        p->gamma    = 0.80f;
        p->low_gain = 3.0f;
        p->mid_gain = -2.0f;
        p->high_gain= 2.0f;
        p->wet      = 0.40f;
        p->amp      = model;
        break;
    case BUS_VOCAL:
        p->drive    = 0.6f;
        p->delta    = 0.10f;
        p->presence = 4.0f;
        p->mid_freq = 2500.0f;
        p->mid_gain = 2.0f;
        p->wet      = 0.30f;
        p->amp      = model;
        break;
    default: break;
    }
}
