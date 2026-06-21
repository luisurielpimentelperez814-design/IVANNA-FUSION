#ifndef NEON_OPTIMIZER_H
#define NEON_OPTIMIZER_H
#include <vector>

namespace NEONOptimizer {
    bool isNEONAvailable();
    void vectorAdd(const float* a, const float* b, float* out, int size);
    void vectorMul(const float* a, const float* b, float* out, int size);
    void vectorScale(const float* in, float* out, int size, float scale);
    void applyEQ(const float* in, float* out, int size, const float* gains, int numBands);
    void applyCompression(float* buffer, int size, float threshold, float ratio, float attack, float release);
    void applyGain(float* buffer, int size, float gainDb);
    void applyLimiter(float* buffer, int size, float ceiling);
    void applyStereoWidth(float* buffer, int numSamples, float width);
    void applyStereoBalance(float* buffer, int numSamples, float balance);
    void mixDryWet(const float* dry, const float* wet, float* out, int size, float mix);
    void applyWindow(float* buffer, int size, int windowType);
}
#endif
