#include "dynamic_eq.h"
#include <cmath>
#define M_PI 3.14159265358979323846

void DynamicEqBand::updateCoefficients(float sr) {
    float A = powf(10.0f, gainDb / 40.0f);
    float w0 = 2.0f * M_PI * frequency / sr;
    float alpha = sinf(w0) / (2.0f * q);
    float cosw0 = cosf(w0);
    float b0 = 1.0f + alpha * A, b1 = -2.0f * cosw0, b2 = 1.0f - alpha * A;
    float a0 = 1.0f + alpha / A, a1 = -2.0f * cosw0, a2 = 1.0f - alpha / A;
    coeff_b0 = b0/a0; coeff_b1 = b1/a0; coeff_b2 = b2/a0;
    coeff_a1 = a1/a0; coeff_a2 = a2/a0;
}

DynamicEQ::DynamicEQ(int n, float sr) : sampleRate(sr) {
    bands.resize(n);
    float freqs[] = {60, 125, 250, 500, 1000, 2000, 4000, 8000};
    for (int i = 0; i < n && i < 8; i++) {
        bands[i].frequency = freqs[i];
        bands[i].q = 1.4f;
        bands[i].updateCoefficients(sr);
    }
}

DynamicEQ::~DynamicEQ() {}

void DynamicEQ::process(float* buf, int n, int ch) {
    for (int s = 0; s < n; s++) {
        for (int c = 0; c < ch; c++) {
            int idx = s*ch+c;
            float sample = buf[idx], proc = 0.0f;
            for (int b = 0; b < (int)bands.size(); b++) {
                float out = bands[b].coeff_b0*sample + bands[b].coeff_b1*bands[b].x1 +
                           bands[b].coeff_b2*bands[b].x2 - bands[b].coeff_a1*bands[b].y1 -
                           bands[b].coeff_a2*bands[b].y2;
                bands[b].x2=bands[b].x1; bands[b].x1=sample;
                bands[b].y2=bands[b].y1; bands[b].y1=out;
                proc += out;
            }
            buf[idx] = sample*0.5f + proc*0.5f;
        }
    }
}
