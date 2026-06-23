#pragma once
// Shim de compatibilidad: expone BiquadFilter con API set_coeffs/process
// que usa ivanna_test.cpp, delegando a BiquadFilterSimd (SIMD real).
#include "biquad_simd.h"
#include <cstdint>

namespace ivanna::dsp {

class BiquadFilter : public BiquadFilterSimd {
public:
    void set_coeffs(float b0, float b1, float b2, float a1, float a2) noexcept {
        setCoeffs({b0, b1, b2, a1, a2});
    }
    // in puede ser == out (in-place); si no, copia primero
    void process(const float* in, float* out, uint32_t frames) noexcept {
        if (out != in) {
            for (uint32_t i = 0; i < frames; ++i) out[i] = in[i];
        }
        processBlock(out, frames);
    }
};

} // namespace ivanna::dsp
