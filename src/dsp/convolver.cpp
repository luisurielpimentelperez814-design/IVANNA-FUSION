#include "convolver.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <fstream>

#ifndef M_PI#define M_PI 3.14159265358979323846
#endif

Convolver::Convolver(int block) : blockSize(block), fftSize(block * 2), 
    irLength(0), mix(0.3f), gainDb(0.0f), preDelaySamples(0), enabled(true),
    fft(fftSize) {
    channelStates.resize(2);
}

Convolver::~Convolver() {}

std::vector<float> Convolver::synthesizeDecayIR(int lengthSamples, float decayTime, float diffusion) {
    std::vector<float> ir(lengthSamples, 0.0f);
    
    float decayRate = 6.9f / (decayTime * 44100.0f);
    
    int earlyReflections[] = {0, 231, 487, 823, 1247, 1891, 2543, 3421};
    float earlyGains[] = {1.0f, 0.7f, 0.5f, 0.4f, 0.3f, 0.25f, 0.2f, 0.15f};
    
    for (int i = 0; i < 8; i++) {
        if (earlyReflections[i] < lengthSamples) {
            ir[earlyReflections[i]] = earlyGains[i];
        }
    }
    
    for (int i = 0; i < lengthSamples; i++) {
        float envelope = expf(-decayRate * i);
        float noise = ((float)rand() / RAND_MAX) * 2.0f - 1.0f;
        ir[i] += noise * envelope * diffusion * 0.3f;
    }
    
    float lpState = 0.0f;
    float lpCoeff = 0.2f;
    for (int i = 0; i < lengthSamples; i++) {
        lpState = lpState * (1.0f - lpCoeff) + ir[i] * lpCoeff;
        ir[i] = lpState;
    }
    
    float peak = 0.0f;
    for (int i = 0; i < lengthSamples; i++) {
        peak = std::max(peak, fabsf(ir[i]));
    }
    if (peak > 0) {
        for (int i = 0; i < lengthSamples; i++) ir[i] /= peak;
    }
    
    return ir;
}

bool Convolver::loadIR(const float* irData, int length, int channels) {    if (!irData || length <= 0) return false;
    
    irLength = length;
    
    for (int c = 0; c < 2; c++) {
        auto& state = channelStates[c];
        
        std::vector<float> ir(length);
        for (int i = 0; i < length; i++) {
            ir[i] = (channels == 1) ? irData[i] : irData[i * channels + c];
        }
        
        float gainLinear = powf(10.0f, gainDb / 20.0f);
        for (auto& s : ir) s *= gainLinear;
        
        std::vector<float> padded(fftSize, 0.0f);
        std::copy(ir.begin(), ir.end(), padded.begin());
        
        std::vector<std::complex<float>> irSpectrum(fftSize);
        fft.forwardFFT(padded.data(), irSpectrum.data(), fftSize);
        
        state.irSpectrum = irSpectrum;
        state.inputBuffer.assign(fftSize, 0.0f);
        state.outputBuffer.assign(fftSize, 0.0f);
        state.overlapBuffer.assign(blockSize, 0.0f);
        state.preDelayBuffer.assign(preDelaySamples + 1, 0.0f);
        state.inputPos = 0;
    }
    
    return true;
}

void Convolver::convolveBlock(ChannelState& state, const float* input, float* output, int numSamples) {
    for (int i = 0; i < numSamples; i++) {
        state.inputBuffer[state.inputPos + i] = input[i];
    }
    
    std::vector<std::complex<float>> inputSpectrum(fftSize);
    fft.forwardFFT(state.inputBuffer.data(), inputSpectrum.data(), fftSize);
    
    std::vector<std::complex<float>> result(fftSize);
    for (int i = 0; i < fftSize; i++) {
        result[i] = inputSpectrum[i] * state.irSpectrum[i];
    }
    
    std::vector<float> timeDomain(fftSize);
    fft.inverseFFT(result.data(), timeDomain.data(), fftSize);
    
    for (int i = 0; i < numSamples; i++) {
        output[i] = timeDomain[state.inputPos + i] + state.overlapBuffer[i];    }
    
    for (int i = 0; i < numSamples; i++) {
        state.overlapBuffer[i] = timeDomain[state.inputPos + numSamples + i];
    }
    
    state.inputPos = (state.inputPos + numSamples) % blockSize;
    if (state.inputPos == 0) {
        std::copy(state.inputBuffer.begin() + blockSize, state.inputBuffer.end(),
                  state.inputBuffer.begin());
        std::fill(state.inputBuffer.begin() + blockSize, state.inputBuffer.end(), 0.0f);
    }
}

void Convolver::process(float* buffer, int numSamples, int channels) {
    if (!enabled || irLength == 0) return;
    
    std::vector<float> wetL(numSamples, 0.0f);
    std::vector<float> wetR(numSamples, 0.0f);
    
    if (channels >= 1) {
        std::vector<float> inL(numSamples);
        for (int i = 0; i < numSamples; i++) inL[i] = buffer[i * channels];
        convolveBlock(channelStates[0], inL.data(), wetL.data(), numSamples);
    }
    if (channels >= 2) {
        std::vector<float> inR(numSamples);
        for (int i = 0; i < numSamples; i++) inR[i] = buffer[i * channels + 1];
        convolveBlock(channelStates[1], inR.data(), wetR.data(), numSamples);
    } else {
        wetR = wetL;
    }
    
    for (int i = 0; i < numSamples; i++) {
        channelStates[0].preDelayBuffer[channelStates[0].preDelayPos] = wetL[i];
        int readPos = (channelStates[0].preDelayPos - preDelaySamples + 
                      channelStates[0].preDelayBuffer.size()) % channelStates[0].preDelayBuffer.size();
        wetL[i] = channelStates[0].preDelayBuffer[readPos];
        
        if (channels >= 2) {
            channelStates[1].preDelayBuffer[channelStates[1].preDelayPos] = wetR[i];
            int readPos = (channelStates[1].preDelayPos - preDelaySamples + 
                          channelStates[1].preDelayBuffer.size()) % channelStates[1].preDelayBuffer.size();
            wetR[i] = channelStates[1].preDelayBuffer[readPos];
        }
        
        channelStates[0].preDelayPos = (channelStates[0].preDelayPos + 1) % channelStates[0].preDelayBuffer.size();
        if (channels >= 2) {
            channelStates[1].preDelayPos = (channelStates[1].preDelayPos + 1) % channelStates[1].preDelayBuffer.size();
        }    }
    
    float dry = 1.0f - mix;
    float wet = mix;
    
    for (int i = 0; i < numSamples; i++) {
        if (channels >= 1) {
            buffer[i * channels] = buffer[i * channels] * dry + wetL[i] * wet;
        }
        if (channels >= 2) {
            buffer[i * channels + 1] = buffer[i * channels + 1] * dry + wetR[i] * wet;
        }
    }
}

void Convolver::setMix(float m) { mix = std::max(0.0f, std::min(1.0f, m)); }
void Convolver::setGain(float g) { gainDb = g; }
void Convolver::setPreDelay(int samples) { preDelaySamples = std::max(0, samples); }

void Convolver::useSmallRoomIR() {
    auto ir = synthesizeDecayIR(4096, 0.4f, 0.6f);
    loadIR(ir.data(), ir.size(), 1);
}

void Convolver::useMediumRoomIR() {
    auto ir = synthesizeDecayIR(8192, 0.9f, 0.7f);
    loadIR(ir.data(), ir.size(), 1);
}

void Convolver::useLargeHallIR() {
    auto ir = synthesizeDecayIR(16384, 2.5f, 0.85f);
    loadIR(ir.data(), ir.size(), 1);
}

void Convolver::usePlateReverbIR() {
    auto ir = synthesizeDecayIR(8192, 1.8f, 0.95f);
    loadIR(ir.data(), ir.size(), 1);
}

void Convolver::useCabinetIR() {
    std::vector<float> ir(2048, 0.0f);
    ir[0] = 1.0f;
    
    float lpState = 0.0f;
    for (int i = 1; i < 2048; i++) {
        ir[i] = ((float)rand() / RAND_MAX - 0.5f) * expf(-i / 400.0f);
    }
    
    for (int i = 0; i < 2048; i++) {
        lpState = lpState * 0.85f + ir[i] * 0.15f;        ir[i] = lpState;
    }
    
    loadIR(ir.data(), ir.size(), 1);
}

void Convolver::useHeadphoneCrossfeedIR() {
    std::vector<float> ir(512, 0.0f);
    ir[0] = 0.8f;
    ir[8] = 0.3f;
    ir[24] = 0.15f;
    
    for (int i = 0; i < 512; i++) {
        ir[i] *= expf(-i / 100.0f);
    }
    
    loadIR(ir.data(), ir.size(), 1);
}
