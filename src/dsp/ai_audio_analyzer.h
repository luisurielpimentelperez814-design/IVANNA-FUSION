#ifndef AI_AUDIO_ANALYZER_H
#define AI_AUDIO_ANALYZER_H
#include <vector>
#include <string>
#include <complex>
#include "../fft/fft_processor.h"

struct AudioFeatures {
    float spectralCentroid, spectralRolloff, spectralFlatness, spectralFlux;
    float rmsEnergy, peakEnergy, zeroCrossingRate;
    float tempo, transientDensity;
    float mfcc[13];
    float bandEnergy[10];
    float dynamicRange, crestFactor;
    float stereoWidth, stereoBalance;
};

enum class ContentType {
    Unknown, Speech, Classical, Rock, Electronic, Jazz, Pop,
    HipHop, Movie, Gaming, Podcast
};

struct AIRecommendation {
    std::string category;
    std::string description;
    std::vector<float> eqGains;
    float compressorThreshold;
    float compressorRatio;
    float reverbMix;
    float stereoWidth;
    float loudnessTarget;
    int suggestedPresetId;
};

class AIAudioAnalyzer {
public:
    AIAudioAnalyzer(int sampleRate = 44100, int fftSize = 2048);
    ~AIAudioAnalyzer();
    void analyze(const float* buffer, int numSamples, int channels);
    AudioFeatures getFeatures() const { return smoothedFeatures; }
    ContentType detectContentType() const;
    AIRecommendation generateRecommendation() const;
    float getConfidence() const { return confidence; }
    std::string getContentTypeName() const;
private:
    int sampleRate, fftSize;
    FFTProcessor fft;
    AudioFeatures currentFeatures, smoothedFeatures, prevFeatures;
    std::vector<float> onsetHistory;
    float avgEnergy, peakEnergySmooth;
    float confidence;
    int analysisCount;
    void extractFeatures(const float* buffer, int numSamples, int channels);
    void smoothFeatures();
    float estimateTempo();
    void computeMFCC(const std::vector<std::complex<float>>& spectrum);
    ContentType classifyContent() const;
};
#endif
