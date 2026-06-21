#pragma once
#include "../core/pf_engine.h"

#define PF_LEARN_FFT_SIZE 2048
#define PF_LEARN_HOP_SIZE 512

typedef struct {
    float spectral_centroid;   /* Hz */
    float rms;                 /* 0.0-1.0 */
    float harmonic_density;    /* 0.0-1.0 */
    float low_energy;          /* 20-250 Hz band ratio */
    float mid_energy;          /* 250-4k Hz band ratio */
    float high_energy;         /* 4k-20k Hz band ratio */
    float crest_factor;        /* peak/rms */
    float spectral_flatness;   /* Wiener entropy */
} PFAudioFeatures;

typedef struct {
    float      *samples;
    uint32_t    n_samples;
    uint32_t    sample_rate;
    const char *label;         /* "70s rock", "psychedelic", etc. */
} PFReferenceAudio;

int  pf_learn_from_reference(const PFReferenceAudio *ref, PFParams *out);
void pf_extract_features(const float *pcm, uint32_t n, uint32_t sr,
                         PFAudioFeatures *feat);
void pf_features_to_params(const PFAudioFeatures *feat,
                           const char *label, PFParams *out);
void pf_optimize_params(PFParams *p, const PFAudioFeatures *target,
                        uint32_t iterations);
int  pf_generate_preset(const char *label, const char *out_name);
