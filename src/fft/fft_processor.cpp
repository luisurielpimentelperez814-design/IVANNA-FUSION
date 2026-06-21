#include "fft_processor.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

FFTProcessor::FFTProcessor(int size) : fftSize(size) {
    computeBitReverse();
    computeTwiddleFactors();
}

FFTProcessor::~FFTProcessor() {}
void FFTProcessor::computeBitReverse() {
    bitReverse.resize(fftSize);
    for (int i = 0; i < fftSize; i++) {
        int rev = 0, temp = i;
        for (int j = 0; j < (int)log2(fftSize); j++) {
            rev = (rev << 1) | (temp & 1);
            temp >>= 1;
        }
        bitReverse[i] = rev;
    }
}

void FFTProcessor::computeTwiddleFactors() {
    twiddleFactors.resize(fftSize / 2);
    for (int i = 0; i < fftSize / 2; i++) {
        float angle = -2.0f * M_PI * i / fftSize;
        twiddleFactors[i] = std::complex<float>(cosf(angle), sinf(angle));
    }
}

void FFTProcessor::forwardFFT(const float* input, std::complex<float>* output, int size) {
    if (size != fftSize) return;
    for (int i = 0; i < fftSize; i++) {
        output[bitReverse[i]] = std::complex<float>(input[i], 0.0f);
    }
    for (int stage = 1; stage <= (int)log2(fftSize); stage++) {
        int m = 1 << stage;
        int wm = m / 2;
        for (int k = 0; k < fftSize; k += m) {
            for (int j = 0; j < wm; j++) {
                std::complex<float> t = twiddleFactors[j * fftSize / m] * output[k + j + wm];
                std::complex<float> u = output[k + j];
                output[k + j] = u + t;
                output[k + j + wm] = u - t;
            }
        }
    }
}

void FFTProcessor::inverseFFT(const std::complex<float>* input, float* output, int size) {
    if (size != fftSize) return;
    std::vector<std::complex<float>> temp(fftSize);
    for (int i = 0; i < fftSize; i++) {
        temp[bitReverse[i]] = std::conj(input[i]);
    }
    for (int stage = 1; stage <= (int)log2(fftSize); stage++) {
        int m = 1 << stage;
        int wm = m / 2;
        for (int k = 0; k < fftSize; k += m) {
            for (int j = 0; j < wm; j++) {                std::complex<float> t = std::conj(twiddleFactors[j * fftSize / m]) * temp[k + j + wm];
                std::complex<float> u = temp[k + j];
                temp[k + j] = u + t;
                temp[k + j + wm] = u - t;
            }
        }
    }
    for (int i = 0; i < fftSize; i++) {
        output[i] = std::conj(temp[i]).real() / fftSize;
    }
}
