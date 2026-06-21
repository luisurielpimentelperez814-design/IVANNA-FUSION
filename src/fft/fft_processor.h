#ifndef FFT_PROCESSOR_H
#define FFT_PROCESSOR_H
#include <vector>
#include <complex>

class FFTProcessor {
public:
    FFTProcessor(int fftSize);
    ~FFTProcessor();
    void forwardFFT(const float* input, std::complex<float>* output, int size);
    void inverseFFT(const std::complex<float>* input, float* output, int size);
private:
    int fftSize;
    std::vector<int> bitReverse;
    std::vector<std::complex<float>> twiddleFactors;
    void computeBitReverse();
    void computeTwiddleFactors();
};
#endif
