#include "pf_engine.h"
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cmath>

extern float pf_chain_sample(PFBusID bus, float x, const PFParams *p);
extern void  pf_rebuild_eq(PFBusID bus, const PFParams *p);
extern void  pf_process_frame_neon(float *buf, uint32_t n, float gain);

static PFEngineState g_state;
static bool          g_initialized = false;

static PFParams default_params(void) {
    PFParams p = {};
    p.alpha     = 1.0f;
    p.beta      = 0.3f;
    p.gamma     = 0.5f;
    p.delta     = 0.4f;
    p.sigma     = 0.5f;
    p.drive     = 1.0f;
    p.wet       = 0.6f;
    p.low_gain  = 0.0f;
    p.mid_gain  = 0.0f;
    p.high_gain = 0.0f;
    p.mid_freq  = 800.0f;
    p.presence  = 0.0f;
    p.sag       = 0.1f;
    p.bias      = 0.5f;
    p.amp       = AMP_BYPASS;
    return p;
}

int pf_init(uint32_t sr, uint32_t fs) {
    memset(&g_state, 0, sizeof(g_state));
    g_state.sample_rate = sr ? sr : 48000;
    g_state.frame_size  = fs ? fs : 256;
    g_state.bar         = 0;
    g_state.master      = default_params();
    for (int i = 0; i < BUS_COUNT; i++) {
        g_state.bus[i].volume = 1.0f;
        g_state.bus[i].pan    = 0.0f;
        g_state.bus[i].dsp    = default_params();
        g_state.bus[i].mute   = false;
        g_state.bus[i].solo   = false;
        pf_rebuild_eq((PFBusID)i, &g_state.bus[i].dsp);
    }
    pf_rebuild_eq(BUS_MASTER, &g_state.master);
    g_initialized = true;
    return 0;
}

void pf_shutdown(void) { g_initialized = false; }

void pf_process_bus(PFBusID bus, float *in, float *out, uint32_t frames) {
    if (!g_initialized) { memcpy(out, in, frames * sizeof(float)); return; }
    PFBusState *b = &g_state.bus[bus];
    if (b->mute) { memset(out, 0, frames * sizeof(float)); return; }
    for (uint32_t i = 0; i < frames; i++)
        out[i] = pf_chain_sample(bus, in[i], &b->dsp) * b->volume;
}

void pf_process(float **in, float **out, uint32_t frames, uint32_t ch) {
    if (!g_initialized) return;
    static float bus_buf[BUS_COUNT][256];
    for (uint32_t c = 0; c < ch && c < (uint32_t)BUS_COUNT-1; c++)
        pf_process_bus((PFBusID)c, in[c], bus_buf[c], frames);
    /* master bus mix-down */
    memset(bus_buf[BUS_MASTER], 0, frames * sizeof(float));
    for (uint32_t c = 0; c < ch && c < (uint32_t)BUS_COUNT-1; c++)
        for (uint32_t i = 0; i < frames; i++)
            bus_buf[BUS_MASTER][i] += bus_buf[c][i];
    /* master chain */
    for (uint32_t i = 0; i < frames; i++)
        bus_buf[BUS_MASTER][i] =
            pf_chain_sample(BUS_MASTER, bus_buf[BUS_MASTER][i], &g_state.master);
    /* write to all output channels */
    for (uint32_t c = 0; c < ch; c++)
        memcpy(out[c], bus_buf[BUS_MASTER], frames * sizeof(float));
}

void pf_set_params(const PFParams *p) {
    g_state.master = *p;
    pf_rebuild_eq(BUS_MASTER, p);
}
void pf_get_params(PFParams *p) { *p = g_state.master; }
void pf_set_bus_params(PFBusID bus, const PFParams *p) {
    g_state.bus[bus].dsp = *p;
    pf_rebuild_eq(bus, p);
}
void pf_set_amp(PFAmpModel model) {
    g_state.master.amp = model;
    for (int i = 0; i < BUS_COUNT; i++) g_state.bus[i].dsp.amp = model;
}
PFEngineState *pf_get_state(void) { return &g_state; }
void pf_advance_bar(void) { g_state.bar++; }

/* ── AMP→DSP fusion rules ─────────────────────────────────────────── */
void pf_apply_amp_fusion(PFParams *p) {
    switch (p->amp) {
    case AMP_MARSHALL:
    case AMP_ROCK70S:
        p->delta  += 0.15f;
        p->drive  += 0.20f;
        p->sigma  -= 0.10f;
        break;
    case AMP_FENDER:
        p->sigma  += 0.15f;
        p->delta  -= 0.10f;
        break;
    case AMP_VOX:
        /* balanced */
        p->beta   += 0.10f;
        break;
    default: break;
    }
    /* clamp */
    if (p->drive > 4.0f) p->drive = 4.0f;
    if (p->delta > 1.0f) p->delta = 1.0f;
    if (p->sigma > 1.0f) p->sigma = 1.0f;
    if (p->delta < 0.0f) p->delta = 0.0f;
}

/* ── Command parser  alpha=1.2;drive=2.0;wet=0.7 ─────────────────── */
int pf_parse_command(const char *cmd) {
    if (!cmd) return -1;
    PFParams p = g_state.master;
    char buf[256]; strncpy(buf, cmd, 255); buf[255] = 0;
    char *tok = strtok(buf, ";");
    while (tok) {
        char key[64]; float val;
        if (sscanf(tok, "%63[^=]=%f", key, &val) == 2) {
            if      (!strcmp(key,"alpha"))    p.alpha     = val;
            else if (!strcmp(key,"beta"))     p.beta      = val;
            else if (!strcmp(key,"gamma"))    p.gamma     = val;
            else if (!strcmp(key,"delta"))    p.delta     = val;
            else if (!strcmp(key,"sigma"))    p.sigma     = val;
            else if (!strcmp(key,"drive"))    p.drive     = val;
            else if (!strcmp(key,"wet"))      p.wet       = val;
            else if (!strcmp(key,"low"))      p.low_gain  = val;
            else if (!strcmp(key,"mid"))      p.mid_gain  = val;
            else if (!strcmp(key,"high"))     p.high_gain = val;
            else if (!strcmp(key,"presence")) p.presence  = val;
            else if (!strcmp(key,"sag"))      p.sag       = val;
            else if (!strcmp(key,"amp"))      p.amp       = (PFAmpModel)(int)val;
        }
        tok = strtok(NULL, ";");
    }
    pf_apply_amp_fusion(&p);
    pf_set_params(&p);
    return 0;
}

/* ── Preset I/O ──────────────────────────────────────────────────── */
int pf_save_preset(const char *name) {
    char path[256];
    snprintf(path, sizeof(path), PF_PRESET_DIR "%s.pfp", name);
    FILE *f = fopen(path, "wb");
    if (!f) return -1;
    fwrite(&g_state.master, sizeof(PFParams), 1, f);
    fclose(f);
    return 0;
}
int pf_load_preset(const char *name) {
    char path[256];
    snprintf(path, sizeof(path), PF_PRESET_DIR "%s.pfp", name);
    FILE *f = fopen(path, "rb");
    if (!f) return -1;
    PFParams p;
    fread(&p, sizeof(PFParams), 1, f);
    fclose(f);
    pf_set_params(&p);
    return 0;
}
