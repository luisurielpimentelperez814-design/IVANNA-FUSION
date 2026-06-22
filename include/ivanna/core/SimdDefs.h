#pragma once

#if defined(__ARM_NEON) || defined(__ARM_NEON__) || defined(_M_ARM) || defined(_M_ARM64)
    #define IVANNA_SIMD_NEON 1
    #include <arm_neon.h>
#endif

#if defined(__AVX2__)
    #define IVANNA_SIMD_AVX2 1
    #include <immintrin.h>
#elif defined(__SSE2__) || defined(_M_X64) || defined(_M_IX86)
    #define IVANNA_SIMD_SSE2 1
    #include <emmintrin.h>
#endif

#if !defined(IVANNA_SIMD_NEON) && !defined(IVANNA_SIMD_AVX2) && !defined(IVANNA_SIMD_SSE2)
    #define IVANNA_SIMD_SCALAR 1
#endif

namespace ivanna::simd {    constexpr size_t ALIGNMENT = 
#if defined(IVANNA_SIMD_AVX2)
        32;
#elif defined(IVANNA_SIMD_NEON) || defined(IVANNA_SIMD_SSE2)
        16;
#else
        8;
#endif
}
