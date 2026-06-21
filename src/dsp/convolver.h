#ifndef CONVOLVER_H
#define CONVOLVER_H

#include <vector>
#include <complex>
#include <string>
#include "../fft/fft_processor.h"

class Convolver {
public:
    Convolver(int blockSize = 1024);
    ~Convolver();
    
    bool loadIR(const float* irData, int irLength, int channels);
    bool loadIRFromFile(const std::string& path);
    void process(float* buffer, int numSamples, int channels);
    
    void setMix(float mix);
    void setGain(float gainDb);
    void setPreDelay(int samples);
    void setEnabled(bool enabled) { this->enabled = enabled; }
    
    float getMix() const { return mix; }
    float getGain() const { return gainDb; }    int getIRLength() const { return irLength; }
    bool isEnabled() const { return enabled; }
    
    void useSmallRoomIR();
    void useMediumRoomIR();
    void useLargeHallIR();
    void usePlateReverbIR();
    void useCabinetIR();
    void useHeadphoneCrossfeedIR();
    
private:
    int blockSize;
    int fftSize;
    int irLength;
    float mix;
    float gainDb;
    int preDelaySamples;
    bool enabled;
    
    FFTProcessor fft;
    
    struct ChannelState {
        std::vector<std::complex<float>> irSpectrum;
        std::vector<float> inputBuffer;
        std::vector<float> outputBuffer;
        std::vector<float> overlapBuffer;
        std::vector<float> preDelayBuffer;
        int inputPos;
        int preDelayPos;
        
        ChannelState() : inputPos(0), preDelayPos(0) {}
    };
    
    std::vector<ChannelState> channelStates;
    
    void convolveBlock(ChannelState& state, const float* input, float* output, int numSamples);
    std::vector<float> synthesizeDecayIR(int lengthSamples, float decayTime, float diffusion);
};

#endif
