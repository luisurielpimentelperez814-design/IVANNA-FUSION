// IVANNA-FUSION Core Library Entry Point
// This file ensures CMake has at least one source for ivanna_core target.

#include "ivanna/core/SimdDefs.h"

namespace ivanna {

// Library version info
const char* get_version() noexcept {
    return "1.0.0";
}

const char* get_build_info() noexcept {
#if defined(IVANNA_SIMD_NEON)
    return "ARM NEON";
#elif defined(IVANNA_SIMD_AVX2)
    return "x86 AVX2";
#elif defined(IVANNA_SIMD_SSE2)
    return "x86 SSE2";
#else
    return "Scalar fallback";
#endif
}

} // namespace ivanna
