#ifndef STEREO_WIDENER_H
#define STEREO_WIDENER_H

class StereoWidener {
public:
    StereoWidener(float sr = 44100.0f);
    ~StereoWidener();
    void process(float* buffer, int numSamples);
    void setWidth(float w) { width = w; }
    void setEnabled(bool e) { enabled = e; }
private:
    float sampleRate, width, balance, currentCorrelation, lpState;
    bool enabled;
};
#endif
