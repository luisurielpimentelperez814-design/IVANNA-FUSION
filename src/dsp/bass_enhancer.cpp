#include "bass_enhancer.h"
#include <cmath>
#include <algorithm>

BassEnhancer::BassEnhancer(float sr) : sampleRate(sr), amount(0.5f), frequency(150.0f),
    lpState(0), envelope(0), enabled(true) {}

BassEnhancer::~BassEnhancer() {}

void BassEnhancer::process(float* buf, int n, int ch) {
    if (!enabled) return;
    for (int i = 0; i < n; i++) {
        for (int c = 0; c < ch; c++) {
            int idx = i*ch+c;
            float input = buf[idx];
            float dt = 1.0f/sampleRate, rc = 1.0f/(2.0f*3.14159f*frequency);
            float alpha = dt/(rc+dt);
            lpState = lpState + alpha*(input - lpState);
            float bass = lpState;
            float absBass = fabsf(bass);
            envelope = absBass > envelope ? envelope*0.99f + absBass*0.01f : envelope*0.9f + absBass*0.1f;
            float drive = 1.0f + amount*3.0f;
            float saturated = tanhf(bass*drive);
            float harmonics = (saturated*saturated - 0.5f)*0.5f*amount*std::min(1.0f, envelope*4.0f);
            buf[idx] = input*(1.0f-amount*0.7f) + (input+harmonics)*amount*0.7f;
        }
    }
}
