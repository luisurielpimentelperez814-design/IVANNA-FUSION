#include "ai_audio_analyzer.h"
#include <cmath>
#include <algorithm>
#include <numeric>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

AIAudioAnalyzer::AIAudioAnalyzer(int sr, int fft) : sampleRate(sr), fftSize(fft), fft(fft),
    avgEnergy(0.0f), peakEnergySmooth(0.0f), confidence(0.0f), analysisCount(0) {
    memset(&currentFeatures, 0, sizeof(currentFeatures));
    memset(&smoothedFeatures, 0, sizeof(smoothedFeatures));
    memset(&prevFeatures, 0, sizeof(prevFeatures));
    onsetHistory.resize(200, 0.0f);
}

AIAudioAnalyzer::~AIAudioAnalyzer() {}

void AIAudioAnalyzer::analyze(const float* buffer, int numSamples, int channels) {
    extractFeatures(buffer, numSamples, channels);
    smoothFeatures();
    analysisCount++;
}

void AIAudioAnalyzer::extractFeatures(const float* buffer, int numSamples, int channels) {
    float rms = 0.0f, peak = 0.0f;
    int zeroCross = 0;
    float prevSample = 0.0f;
    int totalSamples = numSamples * channels;
    for (int i = 0; i < totalSamples; i++) {
        float s = buffer[i];
        rms += s * s;
        peak = std::max(peak, fabsf(s));
        if ((prevSample >= 0 && s < 0) || (prevSample < 0 && s >= 0)) zeroCross++;
        prevSample = s;
    }
    currentFeatures.rmsEnergy = sqrtf(rms / totalSamples);
    currentFeatures.peakEnergy = peak;
    currentFeatures.zeroCrossingRate = (float)zeroCross / numSamples;
    currentFeatures.crestFactor = (currentFeatures.rmsEnergy > 0.0001f) ?
        20.0f * log10f(peak / currentFeatures.rmsEnergy) : 0.0f;
    std::vector<float> mono(numSamples, 0.0f);
    for (int i = 0; i < numSamples; i++) {
        if (channels == 1) mono[i] = buffer[i];
        else mono[i] = (buffer[i * channels] + buffer[i * channels + 1]) * 0.5f;
    }
    std::vector<float> windowed(fftSize, 0.0f);
    int copyLen = std::min(numSamples, fftSize);
    for (int i = 0; i < copyLen; i++) {
        float w = 0.5f * (1.0f - cosf(2.0f * M_PI * i / (fftSize - 1)));
        windowed[i] = mono[i] * w;
    }
    std::vector<std::complex<float>> spectrum(fftSize);
    fft.forwardFFT(windowed.data(), spectrum.data(), fftSize);
    float totalEnergy = 0.0f, weightedSum = 0.0f, cumEnergy = 0.0f;
    float rolloffThreshold = 0.0f;
    std::vector<float> magnitudes(fftSize / 2);
    for (int i = 0; i < fftSize / 2; i++) {
        magnitudes[i] = std::abs(spectrum[i]);
        totalEnergy += magnitudes[i];
    }
    rolloffThreshold = totalEnergy * 0.85f;
    float flatnessNum = 0.0f, flatnessDen = 0.0f;
    int flatCount = 0;
    for (int i = 1; i < fftSize / 2; i++) {
        float freq = (float)i * sampleRate / fftSize;
        float mag = magnitudes[i];
        weightedSum += freq * mag;
        cumEnergy += mag;
        if (cumEnergy >= rolloffThreshold && currentFeatures.spectralRolloff == 0.0f) {
            currentFeatures.spectralRolloff = freq;
        }
        if (mag > 0.0001f && i < 500) {
            flatnessNum += logf(mag);
            flatnessDen += mag;
            flatCount++;
        }
    }
    currentFeatures.spectralCentroid = (totalEnergy > 0.0001f) ? weightedSum / totalEnergy : 0.0f;
    if (flatCount > 0) {
        float geoMean = expf(flatnessNum / flatCount);
        float arithMean = flatnessDen / flatCount;
        currentFeatures.spectralFlatness = (arithMean > 0.0001f) ? geoMean / arithMean : 0.0f;
    }
    currentFeatures.spectralFlux = 0.0f;
    for (int i = 0; i < fftSize / 2; i++) {
        float diff = magnitudes[i] - std::abs(prevFeatures.spectralCentroid);
        currentFeatures.spectralFlux += diff * diff;
    }
    int bandEdges[] = {0, 2, 4, 7, 11, 16, 23, 32, 46, 64, 90};
    for (int b = 0; b < 10; b++) {
        float energy = 0.0f;
        for (int i = bandEdges[b]; i < bandEdges[b + 1] && i < fftSize / 2; i++) {
            energy += magnitudes[i] * magnitudes[i];
        }
        currentFeatures.bandEnergy[b] = energy;
    }
    computeMFCC(spectrum);
    if (channels >= 2) {
        float left = 0.0f, right = 0.0f, corr = 0.0f;
        float lSum = 0.0f, rSum = 0.0f;
        for (int i = 0; i < numSamples; i++) {
            float l = buffer[i * channels];
            float r = buffer[i * channels + 1];
            left += l * l;
            right += r * r;
            corr += l * r;
            lSum += l;
            rSum += r;
        }
        left = sqrtf(left / numSamples);
        right = sqrtf(right / numSamples);
        float denom = left * right;
        currentFeatures.stereoWidth = (denom > 0.0001f) ? 1.0f - (corr / numSamples) / denom : 0.0f;
        currentFeatures.stereoBalance = (left + right > 0.0001f) ? (left - right) / (left + right) : 0.0f;
    }
    float transientEnergy = currentFeatures.rmsEnergy - prevFeatures.rmsEnergy;
    onsetHistory.push_back(transientEnergy > 0 ? transientEnergy : 0.0f);
    onsetHistory.erase(onsetHistory.begin());
    currentFeatures.tempo = estimateTempo();
    float transientCount = 0.0f;
    for (size_t i = 1; i < onsetHistory.size(); i++) {
        if (onsetHistory[i] > 0.05f && onsetHistory[i] > onsetHistory[i - 1]) transientCount++;
    }
    currentFeatures.transientDensity = transientCount * sampleRate / (numSamples * onsetHistory.size());
    prevFeatures = currentFeatures;
}

void AIAudioAnalyzer::computeMFCC(const std::vector<std::complex<float>>& spectrum) {
    int numFilters = 13;
    std::vector<float> melEnergies(numFilters, 0.0f);
    for (int i = 0; i < numFilters; i++) {
        int start = i * (fftSize / 2) / numFilters;
        int end = (i + 1) * (fftSize / 2) / numFilters;
        float energy = 0.0f;
        for (int j = start; j < end && j < (int)spectrum.size(); j++) {
            energy += std::abs(spectrum[j]);
        }
        melEnergies[i] = logf(energy + 0.0001f);
    }
    for (int i = 0; i < 13; i++) currentFeatures.mfcc[i] = melEnergies[i];
}

float AIAudioAnalyzer::estimateTempo() {
    if (onsetHistory.size() < 50) return 0.0f;
    float maxCorr = 0.0f;
    int bestLag = 0;
    int minLag = sampleRate * 60 / 200 / (sampleRate / 100);
    int maxLag = sampleRate * 60 / 60 / (sampleRate / 100);
    for (int lag = minLag; lag < maxLag && lag < (int)onsetHistory.size(); lag++) {
        float corr = 0.0f;
        for (size_t i = lag; i < onsetHistory.size(); i++) {
            corr += onsetHistory[i] * onsetHistory[i - lag];
        }
        if (corr > maxCorr) {
            maxCorr = corr;
            bestLag = lag;
        }
    }
    if (bestLag > 0) return 60.0f * 100.0f / bestLag;
    return 0.0f;
}

void AIAudioAnalyzer::smoothFeatures() {
    float alpha = 0.3f;
    smoothedFeatures.spectralCentroid = alpha * currentFeatures.spectralCentroid + (1 - alpha) * smoothedFeatures.spectralCentroid;
    smoothedFeatures.spectralRolloff = alpha * currentFeatures.spectralRolloff + (1 - alpha) * smoothedFeatures.spectralRolloff;
    smoothedFeatures.spectralFlatness = alpha * currentFeatures.spectralFlatness + (1 - alpha) * smoothedFeatures.spectralFlatness;
    smoothedFeatures.rmsEnergy = alpha * currentFeatures.rmsEnergy + (1 - alpha) * smoothedFeatures.rmsEnergy;
    smoothedFeatures.peakEnergy = alpha * currentFeatures.peakEnergy + (1 - alpha) * smoothedFeatures.peakEnergy;
    smoothedFeatures.zeroCrossingRate = alpha * currentFeatures.zeroCrossingRate + (1 - alpha) * smoothedFeatures.zeroCrossingRate;
    smoothedFeatures.tempo = alpha * currentFeatures.tempo + (1 - alpha) * smoothedFeatures.tempo;
    smoothedFeatures.stereoWidth = alpha * currentFeatures.stereoWidth + (1 - alpha) * smoothedFeatures.stereoWidth;
    smoothedFeatures.stereoBalance = alpha * currentFeatures.stereoBalance + (1 - alpha) * smoothedFeatures.stereoBalance;
    for (int i = 0; i < 10; i++)
        smoothedFeatures.bandEnergy[i] = alpha * currentFeatures.bandEnergy[i] + (1 - alpha) * smoothedFeatures.bandEnergy[i];
    avgEnergy = 0.95f * avgEnergy + 0.05f * currentFeatures.rmsEnergy;
    peakEnergySmooth = 0.99f * peakEnergySmooth + 0.01f * currentFeatures.peakEnergy;
    smoothedFeatures.dynamicRange = (avgEnergy > 0.0001f) ? 20.0f * log10f(peakEnergySmooth / avgEnergy) : 0.0f;
}

ContentType AIAudioAnalyzer::detectContentType() const {
    return classifyContent();
}

ContentType AIAudioAnalyzer::classifyContent() const {
    const auto& f = smoothedFeatures;
    float scores[11] = {0};
    if (f.zeroCrossingRate > 0.1f && f.spectralCentroid > 1500 && f.spectralCentroid < 4000) {
        scores[1] += 3.0f;
        scores[9] += 2.0f;
    }
    if (f.spectralCentroid < 800 && f.rmsEnergy > 0.1f) {
        scores[3] += 2.0f;
        scores[7] += 2.0f;
    }
    if (f.spectralCentroid > 3000 && f.spectralFlatness < 0.3f) {
        scores[4] += 2.5f;
    }
    if (f.spectralFlatness > 0.5f && f.spectralCentroid > 2000) {
        scores[4] += 2.0f;
    }
    if (f.bandEnergy[0] > f.bandEnergy[5] * 2.0f) {
        scores[7] += 2.0f;
        scores[3] += 1.5f;
    }
    if (f.spectralCentroid > 1000 && f.spectralCentroid < 2500 && f.dynamicRange > 10) {
        scores[5] += 2.5f;
    }
    if (f.spectralCentroid > 1500 && f.spectralCentroid < 3000 && f.rmsEnergy > 0.05f) {
        scores[6] += 2.0f;
    }
    if (f.spectralCentroid < 500 && f.zeroCrossingRate < 0.05f) {
        scores[2] += 2.5f;
    }
    if (f.transientDensity > 5.0f) {
        scores[8] += 2.0f;
        scores[3] += 1.5f;
    }
    int bestIdx = 0;
    float bestScore = scores[0];
    for (int i = 1; i < 11; i++) {
        if (scores[i] > bestScore) {
            bestScore = scores[i];
            bestIdx = i;
        }
    }
    confidence = std::min(1.0f, bestScore / 5.0f);
    return static_cast<ContentType>(bestIdx);
}

AIRecommendation AIAudioAnalyzer::generateRecommendation() const {
    AIRecommendation rec;
    rec.eqGains.resize(10, 0.0f);
    ContentType type = classifyContent();
    const auto& f = smoothedFeatures;
    switch (type) {
        case ContentType::Speech:
        case ContentType::Podcast:
            rec.category = "Speech";
            rec.description = "Optimizado para claridad vocal";
            rec.eqGains = {-2, -1, 0, 1, 2, 3, 3, 2, 1, 0};
            rec.compressorThreshold = -18.0f;
            rec.compressorRatio = 3.0f;
            rec.reverbMix = 0.05f;
            rec.stereoWidth = 0.8f;
            rec.loudnessTarget = -16.0f;
            rec.suggestedPresetId = 12;
            break;
        case ContentType::Classical:
            rec.category = "Classical";
            rec.description = "Preservando dinámica natural";
            rec.eqGains = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            rec.compressorThreshold = -24.0f;
            rec.compressorRatio = 1.5f;
            rec.reverbMix = 0.15f;
            rec.stereoWidth = 1.2f;
            rec.loudnessTarget = -20.0f;
            rec.suggestedPresetId = 2;
            break;
        case ContentType::Rock:
            rec.category = "Rock";
            rec.description = "Energía y presencia";
            rec.eqGains = {2, 1, 0, -1, 0, 1, 2, 3, 2, 1};
            rec.compressorThreshold = -15.0f;
            rec.compressorRatio = 4.0f;
            rec.reverbMix = 0.10f;
            rec.stereoWidth = 1.1f;
            rec.loudnessTarget = -10.0f;
            rec.suggestedPresetId = 0;
            break;
        case ContentType::Electronic:
            rec.category = "Electronic";
            rec.description = "Graves profundos y agudos brillantes";
            rec.eqGains = {4, 3, 1, 0, -1, 0, 1, 2, 3, 2};
            rec.compressorThreshold = -12.0f;
            rec.compressorRatio = 3.5f;
            rec.reverbMix = 0.12f;
            rec.stereoWidth = 1.3f;
            rec.loudnessTarget = -9.0f;
            rec.suggestedPresetId = 4;
            break;
        case ContentType::Jazz:
            rec.category = "Jazz";
            rec.description = "Cálido y natural";
            rec.eqGains = {1, 1, 0, 0, 0, 1, 1, 0, -1, -1};
            rec.compressorThreshold = -20.0f;
            rec.compressorRatio = 2.0f;
            rec.reverbMix = 0.10f;
            rec.stereoWidth = 1.0f;
            rec.loudnessTarget = -14.0f;
            rec.suggestedPresetId = 3;
            break;
        case ContentType::Pop:
            rec.category = "Pop";
            rec.description = "Brillante y comercial";
            rec.eqGains = {2, 1, 0, 0, 1, 2, 2, 3, 3, 2};
            rec.compressorThreshold = -14.0f;
            rec.compressorRatio = 3.0f;
            rec.reverbMix = 0.10f;
            rec.stereoWidth = 1.1f;
            rec.loudnessTarget = -11.0f;
            rec.suggestedPresetId = 1;
            break;
        case ContentType::HipHop:
            rec.category = "Hip-Hop";
            rec.description = "Graves potentes y vocales claros";
            rec.eqGains = {4, 3, 1, 0, 0, 1, 2, 2, 1, 0};
            rec.compressorThreshold = -13.0f;
            rec.compressorRatio = 3.5f;
            rec.reverbMix = 0.08f;
            rec.stereoWidth = 1.0f;
            rec.loudnessTarget = -10.0f;
            rec.suggestedPresetId = 5;
            break;
        case ContentType::Movie:
            rec.category = "Movie";
            rec.description = "Experiencia cinematográfica";
            rec.eqGains = {3, 2, 1, 0, 0, 1, 1, 2, 2, 1};
            rec.compressorThreshold = -18.0f;
            rec.compressorRatio = 2.5f;
            rec.reverbMix = 0.20f;
            rec.stereoWidth = 1.4f;
            rec.loudnessTarget = -14.0f;
            rec.suggestedPresetId = 7;
            break;
        case ContentType::Gaming:
            rec.category = "Gaming";
            rec.description = "Posicionamiento espacial preciso";
            rec.eqGains = {2, 1, 0, 0, 1, 2, 3, 3, 2, 1};
            rec.compressorThreshold = -16.0f;
            rec.compressorRatio = 2.5f;
            rec.reverbMix = 0.15f;
            rec.stereoWidth = 1.3f;
            rec.loudnessTarget = -12.0f;
            rec.suggestedPresetId = 9;
            break;
        default:
            rec.category = "Balanced";
            rec.description = "Respuesta plana balanceada";
            rec.eqGains = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            rec.compressorThreshold = -18.0f;
            rec.compressorRatio = 2.0f;
            rec.reverbMix = 0.10f;
            rec.stereoWidth = 1.0f;
            rec.loudnessTarget = -14.0f;
            rec.suggestedPresetId = -1;
    }
    return rec;
}

std::string AIAudioAnalyzer::getContentTypeName() const {
    switch (classifyContent()) {
        case ContentType::Speech: return "Speech";
        case ContentType::Classical: return "Classical";
        case ContentType::Rock: return "Rock";
        case ContentType::Electronic: return "Electronic";
        case ContentType::Jazz: return "Jazz";
        case ContentType::Pop: return "Pop";
        case ContentType::HipHop: return "Hip-Hop";
        case ContentType::Movie: return "Movie";
        case ContentType::Gaming: return "Gaming";
        case ContentType::Podcast: return "Podcast";
        default: return "Unknown";
    }
}
