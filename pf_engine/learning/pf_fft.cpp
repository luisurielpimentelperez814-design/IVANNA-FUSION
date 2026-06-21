#include "pf_fft.h"
#include <cmath>
#include <cstring>

#define TWOPI 6.28318530717958647692f

static void fft_recursive(float *re, float *im, uint32_t n) {
    if (n <= 1) return;
    float *re_e = (float*)__builtin_alloca(n/2*sizeof(float));
    float *im_e = (float*)__builtin_alloca(n/2*sizeof(float));
    float *re_o = (float*)__builtin_alloca(n/2*sizeof(float));
    float *im_o = (float*)__builtin_alloca(n/2*sizeof(float));
    for (uint32_t i = 0; i < n/2; i++) {
        re_e[i]=re[2*i]; im_e[i]=im[2*i];
        re_o[i]=re[2*i+1]; im_o[i]=im[2*i+1];
    }
    fft_recursive(re_e,im_e,n/2);
    fft_recursive(re_o,im_o,n/2);
    for (uint32_t k = 0; k < n/2; k++) {
        float angle = -TWOPI*k/n;
        float wr = cosf(angle), wi = sinf(angle);
        float tr = wr*re_o[k]-wi*im_o[k];
        float ti = wr*im_o[k]+wi*re_o[k];
        re[k]     = re_e[k]+tr; im[k]     = im_e[k]+ti;
        re[k+n/2] = re_e[k]-tr; im[k+n/2] = im_e[k]-ti;
    }
}

void pf_fft_forward(PFFFT *fft, const float *input, uint32_t n) {
    if (n > PF_FFT_SIZE) n = PF_FFT_SIZE;
    memcpy(fft->re, input, n*sizeof(float));
    memset(fft->im, 0,     n*sizeof(float));
    fft->size = n;
    fft_recursive(fft->re, fft->im, n);
}

void pf_fft_magnitude(const PFFFT *fft, float *mag, uint32_t n) {
    for (uint32_t i = 0; i < n; i++)
        mag[i] = sqrtf(fft->re[i]*fft->re[i] + fft->im[i]*fft->im[i]);
}
