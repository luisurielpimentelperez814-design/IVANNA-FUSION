#pragma once
#include "pf_engine.h"

/* BAR milestones */
#define EVO_BAR_BASELINE  0
#define EVO_BAR_BUILD     16
#define EVO_BAR_PEAK      32
#define EVO_BAR_DECAY     48

typedef struct {
    PFParams baseline;
    PFParams build;
    PFParams peak;
    PFParams decay;
} PFEvolutionCurve;

void pf_evo_init  (PFEvolutionCurve *c, const PFParams *base);
void pf_evo_tick  (PFEvolutionCurve *c, uint32_t bar, PFParams *out);
void pf_evo_interp(const PFParams *a, const PFParams *b, float t, PFParams *out);
