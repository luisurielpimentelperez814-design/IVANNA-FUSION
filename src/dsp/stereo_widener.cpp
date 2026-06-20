#include "stereo_widener.h"
#include <cmath>
#include <algorithm>

StereoWidener::StereoWidener(float sr) : sampleRate(sr), width(1.0f), balance(0.0f),
    currentCorrelation(0.0f), lpState(0), enabled(true) {}

StereoWidener::~StereoWidener() {}

void StereoWidener::process(float* buf, int n) {
    if (!enabled) return;
    float rc = expf(-2.0f*3.14159f*150.0f/sampleRate);
    for (int i = 0; i < n; i++) {
        int idx = i*2;
        float l = buf[idx], r = buf[idx+1];
        float mid = (l+r)*0.5f, side = (l-r)*0.5f;
        lpState = (1.0f-rc)*mid + rc*lpState;
        float sideNoBass = side - lpState*0.5f;
        float widenedSide = sideNoBass*width;
        buf[idx] = mid + widenedSide;
        buf[idx+1] = mid - widenedSide;
    }
}
