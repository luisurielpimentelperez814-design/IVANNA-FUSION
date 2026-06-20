#include "neon_optimizer.h"
#include <cmath>
#include <algorithm>

#ifdef __ARM_NEON
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace NEONOptimizer {

bool isNEONAvailable() {
#if HAS_NEON
    return true;
#else
    return false;
#endif
}

void vectorAdd(const float* a, const float* b, float* out, int size) {
#if HAS_NEON
    int i = 0;
    for (; i + 3 < size; i += 4) {
        float32x4_t va = vld1q_f32(a + i);
        float32x4_t vb = vld1q_f32(b + i);
        vst1q_f32(out + i, vaddq_f32(va, vb));
    }
    for (; i < size; i++) out[i] = a[i] + b[i];
#else
    for (int i = 0; i < size; i++) out[i] = a[i] + b[i];
#endif
}

void vectorMul(const float* a, const float* b, float* out, int size) {
#if HAS_NEON
    int i = 0;
    for (; i + 3 < size; i += 4) {
        float32x4_t va = vld1q_f32(a + i);
        float32x4_t vb = vld1q_f32(b + i);
        vst1q_f32(out + i, vmulq_f32(va, vb));
    }
    for (; i < size; i++) out[i] = a[i] * b[i];
#else
    for (int i = 0; i < size; i++) out[i] = a[i] * b[i];
#endif
}

void vectorScale(const float* in, float* out, int size, float scale) {
#if HAS_NEON
    float32x4_t vscale = vdupq_n_f32(scale);
    int i = 0;
    for (; i + 3 < size; i += 4) {
        float32x4_t v = vld1q_f32(in + i);
        vst1q_f32(out + i, vmulq_f32(v, vscale));
    }
    for (; i < size; i++) out[i] = in[i] * scale;
#else
    for (int i = 0; i < size; i++) out[i] = in[i] * scale;
#endif
}

void applyEQ(const float* in, float* out, int size, const float* gains, int numBands) {
    float gainLin = powf(10.0f, gains[0] / 20.0f);
#if HAS_NEON
    float32x4_t vgain = vdupq_n_f32(gainLin);
    int i = 0;
    for (; i + 3 < size; i += 4) {
        float32x4_t v = vld1q_f32(in + i);
        vst1q_f32(out + i, vmulq_f32(v, vgain));
    }
    for (; i < size; i++) out[i] = in[i] * gainLin;
#else
    for (int i = 0; i < size; i++) out[i] = in[i] * gainLin;
#endif
    (void)numBands;
}

void applyCompression(float* buffer, int size, float threshold, float ratio, float attack, float release) {
    float threshLin = powf(10.0f, threshold / 20.0f);
    float attackCoeff = expf(-1.0f / (attack * 44100.0f));
    float releaseCoeff = expf(-1.0f / (release * 44100.0f));
    float env = 0.0f;
    for (int i = 0; i < size; i++) {
        float level = fabsf(buffer[i]);
        if (level > env) env = attackCoeff * env + (1 - attackCoeff) * level;
        else env = releaseCoeff * env + (1 - releaseCoeff) * level;
        if (env > threshLin) {
            float overDb = 20.0f * log10f(env / threshLin);
            float gainReduction = overDb * (1.0f - 1.0f / ratio);
            float gain = powf(10.0f, -gainReduction / 20.0f);
            buffer[i] *= gain;
        }
    }
}

void applyGain(float* buffer, int size, float gainDb) {
    float gain = powf(10.0f, gainDb / 20.0f);
#if HAS_NEON
    float32x4_t vgain = vdupq_n_f32(gain);
    int i = 0;
    for (; i + 3 < size; i += 4) {
        float32x4_t v = vld1q_f32(buffer + i);
        vst1q_f32(buffer + i, vmulq_f32(v, vgain));
    }
    for (; i < size; i++) buffer[i] *= gain;
#else
    for (int i = 0; i < size; i++) buffer[i] *= gain;
#endif
}

void applyLimiter(float* buffer, int size, float ceiling) {
    for (int i = 0; i < size; i++) {
        if (buffer[i] > ceiling) buffer[i] = ceiling;
        else if (buffer[i] < -ceiling) buffer[i] = -ceiling;
    }
}

void applyStereoWidth(float* buffer, int numSamples, float width) {
    for (int i = 0; i < numSamples; i++) {
        float l = buffer[i * 2];
        float r = buffer[i * 2 + 1];
        float mid = (l + r) * 0.5f;
        float side = (l - r) * 0.5f;
        side *= width;
        buffer[i * 2] = mid + side;
        buffer[i * 2 + 1] = mid - side;
    }
}

void applyStereoBalance(float* buffer, int numSamples, float balance) {
    float leftGain = std::min(1.0f, 1.0f - balance);
    float rightGain = std::min(1.0f, 1.0f + balance);
    for (int i = 0; i < numSamples; i++) {
        buffer[i * 2] *= leftGain;
        buffer[i * 2 + 1] *= rightGain;
    }
}

void mixDryWet(const float* dry, const float* wet, float* out, int size, float mix) {
    float dryGain = 1.0f - mix;
    float wetGain = mix;
#if HAS_NEON
    float32x4_t vd = vdupq_n_f32(dryGain);
    float32x4_t vw = vdupq_n_f32(wetGain);
    int i = 0;
    for (; i + 3 < size; i += 4) {
        float32x4_t vdr = vld1q_f32(dry + i);
        float32x4_t vwt = vld1q_f32(wet + i);
        float32x4_t res = vaddq_f32(vmulq_f32(vdr, vd), vmulq_f32(vwt, vw));
        vst1q_f32(out + i, res);
    }
    for (; i < size; i++) out[i] = dry[i] * dryGain + wet[i] * wetGain;
#else
    for (int i = 0; i < size; i++) out[i] = dry[i] * dryGain + wet[i] * wetGain;
#endif
}

void applyWindow(float* buffer, int size, int windowType) {
    for (int i = 0; i < size; i++) {
        float w;
        if (windowType == 0) {
            w = 0.5f * (1.0f - cosf(2.0f * M_PI * i / (size - 1)));
        } else {
            float a = 0.42f, b = 0.5f, c = 0.08f;
            w = a - b * cosf(2.0f * M_PI * i / (size - 1)) + c * cosf(4.0f * M_PI * i / (size - 1));
        }
        buffer[i] *= w;
    }
}

}
