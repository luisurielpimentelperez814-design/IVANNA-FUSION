#include "pf_learning.h"
#include "pf_fft.h"
#include <cmath>
#include <cstring>
#include <cstdio>
#include <cstdlib>

#define SR_F 48000.0f

// ── RMS ───────────────────────────────────────────────────────────────
static float calc_rms(const float *x, uint32_t n) {
    float acc = 0.0f;
    for (uint32_t i = 0; i < n; i++) acc += x[i]*x[i];
    return sqrtf(acc / (float)n);
}

static float calc_peak(const float *x, uint32_t n) {
    float pk = 0.0f;
    for (uint32_t i = 0; i < n; i++) {
        float a = fabsf(x[i]);
        if (a > pk) pk = a;
    }
    return pk;
}

// ── Spectral centroid  f_c = sum(f*mag) / sum(mag) ───────────────────
static float calc_centroid(const float *mag, uint32_t n, float sr) {
    float num = 0.0f, den = 0.0f;
    float bin_hz = sr / (float)(n*2);
    for (uint32_t i = 1; i < n; i++) {
        float f = i * bin_hz;
        num += f * mag[i];
        den += mag[i];
    }
    return (den > 1e-9f) ? num/den : 0.0f;
}

// ── Band energy ratio ─────────────────────────────────────────────────
static float band_energy(const float *mag, uint32_t n,
                          float sr, float f_lo, float f_hi) {
    float bin_hz = sr / (float)(n*2);
    uint32_t lo  = (uint32_t)(f_lo / bin_hz);
    uint32_t hi  = (uint32_t)(f_hi / bin_hz);
    if (hi >= n) hi = n-1;
    float acc = 0.0f, total = 0.0f;
    for (uint32_t i = 1; i < n; i++) {
        total += mag[i];
        if (i >= lo && i <= hi) acc += mag[i];
    }
    return (total > 1e-9f) ? acc/total : 0.0f;
}

// ── Spectral flatness  exp(mean(log(mag))) / mean(mag) ───────────────
static float calc_flatness(const float *mag, uint32_t n) {
    float log_sum = 0.0f, lin_sum = 0.0f;
    uint32_t cnt = 0;
    for (uint32_t i = 1; i < n; i++) {
        if (mag[i] > 1e-9f) {
            log_sum += logf(mag[i]);
            lin_sum += mag[i];
            cnt++;
        }
    }
    if (cnt == 0 || lin_sum < 1e-9f) return 0.0f;
    return expf(log_sum/(float)cnt) / (lin_sum/(float)cnt);
}

// ── Harmonic density: ratio of energy in harmonic partials ───────────
static float calc_harmonic_density(const float *mag, uint32_t n, float sr,
                                    float f0) {
    if (f0 < 20.0f) return 0.0f;
    float bin_hz = sr / (float)(n*2);
    float total  = 0.0f, harm = 0.0f;
    for (uint32_t i = 1; i < n; i++) total += mag[i];
    if (total < 1e-9f) return 0.0f;
    /* sum energy within ±2 bins of each harmonic up to 10th */
    for (int h = 1; h <= 10; h++) {
        float fh = f0 * h;
        if (fh > sr/2.0f) break;
        int bin = (int)(fh / bin_hz);
        for (int d = -2; d <= 2; d++) {
            int b = bin+d;
            if (b > 0 && b < (int)n) harm += mag[b];
        }
    }
    return fminf(harm/total, 1.0f);
}

// ── Main feature extractor ───────────────────────────────────────────
void pf_extract_features(const float *pcm, uint32_t n, uint32_t sr,
                          PFAudioFeatures *feat) {
    static PFFFT fft;
    uint32_t fft_n = PF_LEARN_FFT_SIZE;
    if (n < fft_n) fft_n = n;

    /* apply Hann window */
    static float windowed[PF_LEARN_FFT_SIZE];
    for (uint32_t i = 0; i < fft_n; i++) {
        float w = 0.5f*(1.0f - cosf(6.28318f*i/(float)(fft_n-1)));
        windowed[i] = pcm[i] * w;
    }

    pf_fft_forward(&fft, windowed, fft_n);
    static float mag[PF_LEARN_FFT_SIZE];
    pf_fft_magnitude(&fft, mag, fft_n/2);

    float sr_f = (float)sr;
    feat->rms              = calc_rms(pcm, n);
    feat->spectral_centroid= calc_centroid(mag, fft_n/2, sr_f);
    feat->low_energy       = band_energy(mag, fft_n/2, sr_f, 20.0f,   250.0f);
    feat->mid_energy       = band_energy(mag, fft_n/2, sr_f, 250.0f, 4000.0f);
    feat->high_energy      = band_energy(mag, fft_n/2, sr_f, 4000.0f,20000.0f);
    feat->spectral_flatness= calc_flatness(mag, fft_n/2);
    feat->crest_factor     = calc_peak(pcm,n) / (feat->rms + 1e-9f);
    /* estimate f0 as bin of peak magnitude */
    uint32_t peak_bin = 1;
    for (uint32_t i = 2; i < fft_n/2; i++)
        if (mag[i] > mag[peak_bin]) peak_bin = i;
    float f0 = peak_bin * (sr_f / (float)fft_n);
    feat->harmonic_density = calc_harmonic_density(mag, fft_n/2, sr_f, f0);
}

// ── Feature → DSP parameter mapping ─────────────────────────────────
void pf_features_to_params(const PFAudioFeatures *f,
                            const char *label, PFParams *p) {
    memset(p, 0, sizeof(*p));

    /* base from spectral character */
    p->alpha    = 0.8f + f->spectral_flatness * 0.4f;
    p->beta     = f->harmonic_density;
    p->gamma    = 1.0f - fminf(f->crest_factor / 20.0f, 1.0f);
    p->delta    = f->mid_energy * 1.2f;
    p->sigma    = f->spectral_flatness * 0.8f;
    p->drive    = 0.5f + f->rms * 3.0f + f->harmonic_density * 1.5f;
    p->wet      = 0.4f + f->harmonic_density * 0.4f;
    p->low_gain = (f->low_energy  - 0.33f) * 12.0f;
    p->mid_gain = (f->mid_energy  - 0.33f) *  8.0f;
    p->high_gain= (f->high_energy - 0.33f) *  8.0f;
    p->mid_freq = 200.0f + f->spectral_centroid * 0.5f;
    p->presence = f->high_energy * 6.0f;
    p->sag      = 0.05f + f->rms * 0.2f;
    p->bias     = 0.50f;

    /* label-based amp selection */
    if (label) {
        if (strstr(label,"marshall") || strstr(label,"metal") ||
            strstr(label,"crunch"))          p->amp = AMP_MARSHALL;
        else if (strstr(label,"fender") || strstr(label,"clean") ||
                 strstr(label,"warm"))       p->amp = AMP_FENDER;
        else if (strstr(label,"vox") || strstr(label,"brit") ||
                 strstr(label,"sparkle"))    p->amp = AMP_VOX;
        else if (strstr(label,"70s") || strstr(label,"rock") ||
                 strstr(label,"psychedelic"))p->amp = AMP_ROCK70S;
        else                                 p->amp = AMP_ROCK70S;
    } else { p->amp = AMP_ROCK70S; }

    /* clamp */
    if (p->drive > 4.0f)  p->drive  = 4.0f;
    if (p->wet   > 1.0f)  p->wet    = 1.0f;
    if (p->alpha > 1.5f)  p->alpha  = 1.5f;
}

// ── Gradient descent optimizer (50 iter) ─────────────────────────────
void pf_optimize_params(PFParams *p, const PFAudioFeatures *target,
                         uint32_t iterations) {
    float lr = 0.02f;
    for (uint32_t iter = 0; iter < iterations; iter++) {
        /* proxy cost: align drive to rms+harmonic target */
        float drive_target = 0.5f + target->rms*3.0f + target->harmonic_density*1.5f;
        float wet_target   = 0.4f + target->harmonic_density*0.4f;
        float beta_target  = target->harmonic_density;
        float delta_target = target->mid_energy * 1.2f;
        float err_drive = drive_target - p->drive;
        float err_wet   = wet_target   - p->wet;
        float err_beta  = beta_target  - p->beta;
        float err_delta = delta_target - p->delta;
        p->drive += lr * err_drive;
        p->wet   += lr * err_wet;
        p->beta  += lr * err_beta;
        p->delta += lr * err_delta;
        lr *= 0.97f;  /* decay learning rate */
    }
    /* clamp after optimize */
    if (p->drive > 4.0f) p->drive = 4.0f;
    if (p->drive < 0.1f) p->drive = 0.1f;
    if (p->wet   > 1.0f) p->wet   = 1.0f;
    if (p->wet   < 0.0f) p->wet   = 0.0f;
    if (p->beta  > 1.0f) p->beta  = 1.0f;
    if (p->beta  < 0.0f) p->beta  = 0.0f;
    if (p->delta > 1.0f) p->delta = 1.0f;
    if (p->delta < 0.0f) p->delta = 0.0f;
}

// ── Full learning pipeline ────────────────────────────────────────────
int pf_learn_from_reference(const PFReferenceAudio *ref, PFParams *out) {
    if (!ref || !ref->samples || ref->n_samples < PF_LEARN_FFT_SIZE)
        return -1;

    PFAudioFeatures feat;
    pf_extract_features(ref->samples, ref->n_samples, ref->sample_rate, &feat);
    pf_features_to_params(&feat, ref->label, out);
    pf_optimize_params(out, &feat, 50);

    /* save preset to disk */
    if (ref->label) pf_save_preset(ref->label);
    return 0;
}

// ── Preset generator from label string (no audio needed) ─────────────
int pf_generate_preset(const char *label, const char *out_name) {
    /* synthesize artificial feature targets from label keywords */
    PFAudioFeatures feat = {};
    if (strstr(label,"70s") || strstr(label,"rock")) {
        feat.rms=0.35f; feat.harmonic_density=0.65f;
        feat.mid_energy=0.45f; feat.low_energy=0.35f;
        feat.high_energy=0.20f; feat.spectral_flatness=0.15f;
        feat.crest_factor=4.5f; feat.spectral_centroid=1800.0f;
    } else if (strstr(label,"psychedelic")) {
        feat.rms=0.30f; feat.harmonic_density=0.55f;
        feat.mid_energy=0.40f; feat.low_energy=0.30f;
        feat.high_energy=0.30f; feat.spectral_flatness=0.25f;
        feat.crest_factor=3.5f; feat.spectral_centroid=2400.0f;
    } else if (strstr(label,"clean") || strstr(label,"jazz")) {
        feat.rms=0.20f; feat.harmonic_density=0.25f;
        feat.mid_energy=0.35f; feat.low_energy=0.40f;
        feat.high_energy=0.25f; feat.spectral_flatness=0.40f;
        feat.crest_factor=6.0f; feat.spectral_centroid=900.0f;
    } else {
        feat.rms=0.28f; feat.harmonic_density=0.45f;
        feat.mid_energy=0.40f; feat.low_energy=0.33f;
        feat.high_energy=0.27f; feat.spectral_flatness=0.20f;
        feat.crest_factor=5.0f; feat.spectral_centroid=1500.0f;
    }

    PFParams p;
    pf_features_to_params(&feat, label, &p);
    pf_optimize_params(&p, &feat, 50);
    pf_set_params(&p);
    return pf_save_preset(out_name ? out_name : label);
}
