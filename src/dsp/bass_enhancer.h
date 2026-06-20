#ifndef BASS_ENHANCER_H
#define BASS_ENHANCER_H

class BassEnhancer {
public:
    BassEnhancer(float sr = 44100.0f);
    ~BassEnhancer();
    void process(float* buffer, int numSamples, int channels);
    void setAmount(float a) { amount = a; }
    void setEnabled(bool e) { enabled = e; }
private:
    float sampleRate, amount, frequency, lpState, envelope;
    bool enabled;
};
#endif
