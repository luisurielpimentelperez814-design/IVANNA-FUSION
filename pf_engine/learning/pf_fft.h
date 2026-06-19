#pragma once
#include <stdint.h>
#include <complex.h>

#define PF_FFT_SIZE 2048

typedef struct {
    float    re[PF_FFT_SIZE];
    float    im[PF_FFT_SIZE];
    uint32_t size;
} PFFFT;

void pf_fft_forward(PFFFT *fft, const float *input, uint32_t n);
void pf_fft_magnitude(const PFFFT *fft, float *mag, uint32_t n);
