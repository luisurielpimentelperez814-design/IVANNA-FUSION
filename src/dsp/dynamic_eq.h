#ifndef DYNAMIC_EQ_H
#define DYNAMIC_EQ_H
#include <vector>

struct DynamicEqBand {
    float frequency, q, gainDb, thresholdDb, ratio, attackMs, releaseMs;
    bool enabled;
    float envelope, coeff_b0, coeff_b1, coeff_b2, coeff_a1, coeff_a2, x1, x2, y1, y2;
    
    DynamicEqBand() : frequency(1000), q(1.0f), gainDb(0), thresholdDb(-20), ratio(1.0f),
                      attackMs(10), releaseMs(100), enabled(true), envelope(0),
                      x1(0), x2(0), y1(0), y2(0) {}
    
    void updateCoefficients(float sampleRate);
};

class DynamicEQ {
public:
    DynamicEQ(int numBands = 8, float sampleRate = 44100.0f);
    ~DynamicEQ();
    void process(float* buffer, int numSamples, int channels);
    DynamicEqBand& getBand(int index) { return bands[index]; }
    int getNumBands() const { return bands.size(); }
private:
    std::vector<DynamicEqBand> bands;
    float sampleRate;
};
#endif
